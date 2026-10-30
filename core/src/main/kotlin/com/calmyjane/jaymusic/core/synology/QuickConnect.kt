package com.calmyjane.jaymusic.core.synology

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Resolves a Synology QuickConnect ID (e.g. `myname`) to addresses the NAS can be reached at.
 *
 * Uses the same service as Synology's own apps (`global.quickconnect.to/Serv.php`). The result
 * lists LAN addresses first, then direct internet addresses and finally the QuickConnect relay,
 * which works even without port forwarding.
 */
class QuickConnect(
    private val http: OkHttpClient,
    private val globalServer: String = "https://global.quickconnect.to",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A candidate address, ordered by preference. */
    data class Candidate(val url: String, val kind: Kind)

    enum class Kind { LAN, INTERNET, RELAY }

    suspend fun resolve(id: String): List<Candidate> {
        var infos = query(globalServer, id, "get_server_info")
        // The global server may redirect to the regional server that knows this ID.
        val sites = infos.flatMap { (it["sites"] as? JsonArray).orEmpty() }
            .mapNotNull { (it as? JsonPrimitive)?.content }
        if (infos.none { it.errno() == 0 } && sites.isNotEmpty()) {
            for (site in sites) {
                val regional = runCatching { query("https://$site", id, "get_server_info") }.getOrDefault(emptyList())
                if (regional.any { it.errno() == 0 }) {
                    infos = regional
                    break
                }
            }
        }
        val ok = infos.filter { it.errno() == 0 }
        if (ok.isEmpty()) {
            val errno = infos.firstOrNull()?.errno()
            throw IOException(
                if (errno == 4 || errno == 2) "QuickConnect ID \"$id\" not found" else "QuickConnect lookup failed (error $errno)",
            )
        }

        val candidates = LinkedHashSet<Candidate>()
        for (info in ok) addDirect(info, candidates)

        // Relay: returned directly, or requested from the control host ("tunnel").
        val relays = ok.flatMap { relayOf(it) }
        if (relays.isNotEmpty()) {
            candidates += relays
        } else {
            val controlHost = ok.firstNotNullOfOrNull { (it["env"] as? JsonObject)?.str("control_host") }
            if (controlHost != null) {
                val tunnel = runCatching { query("https://$controlHost", id, "request_tunnel", onlyHttps = true) }
                    .getOrDefault(emptyList())
                candidates += tunnel.filter { it.errno() == 0 }.flatMap { relayOf(it) }
            }
        }
        // Order: LAN, then internet, then relay (LinkedHashSet keeps first insertion order per kind).
        return candidates.sortedBy { it.kind.ordinal }
    }

    private fun addDirect(info: JsonObject, out: MutableSet<Candidate>) {
        val server = info["server"] as? JsonObject ?: return
        val service = info["service"] as? JsonObject
        val https = when (info.str("service_id")) {
            "dsm_https" -> true
            "dsm" -> false
            else -> service?.int("port") in setOf(5001, 443)
        }
        val scheme = if (https) "https" else "http"
        val port = service?.int("port") ?: if (https) 5001 else 5000
        val extPort = service?.int("ext_port")?.takeIf { it > 0 } ?: port
        val smartDns = info["smartdns"] as? JsonObject

        // Synology's "smart DNS" names have valid certificates, so they work with https.
        (smartDns?.get("lan") as? JsonArray)?.forEach { host ->
            (host as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { out += Candidate("$scheme://$it:$port", Kind.LAN) }
        }
        (server["interface"] as? JsonArray)?.forEach { iface ->
            (iface as? JsonObject)?.str("ip")?.takeIf { it.isNotBlank() }?.let { ip ->
                out += Candidate("$scheme://$ip:$port", Kind.LAN)
            }
        }
        smartDns?.str("host")?.takeIf { it.isNotBlank() }?.let { out += Candidate("$scheme://$it:$extPort", Kind.INTERNET) }
        listOfNotNull(server.str("ddns"), server.str("fqdn"))
            .filter { it.isNotBlank() && !it.equals("NULL", ignoreCase = true) }
            .forEach { out += Candidate("$scheme://$it:$extPort", Kind.INTERNET) }
        (server["external"] as? JsonObject)?.str("ip")?.takeIf { it.isNotBlank() && it != "0.0.0.0" }?.let { ip ->
            out += Candidate("$scheme://$ip:$extPort", Kind.INTERNET)
        }
    }

    private fun relayOf(info: JsonObject): List<Candidate> {
        val service = info["service"] as? JsonObject ?: return emptyList()
        val port = service.int("relay_port")?.takeIf { it > 0 } ?: return emptyList()
        return listOfNotNull(service.str("relay_dn"), service.str("relay_ip"))
            .filter { it.isNotBlank() && it != "0.0.0.0" }
            .map { Candidate("https://$it:$port", Kind.RELAY) }
    }

    private suspend fun query(server: String, id: String, command: String, onlyHttps: Boolean = false): List<JsonObject> {
        val services = if (onlyHttps) listOf("dsm_https") else listOf("dsm_https", "dsm")
        val body = JsonArray(
            services.map { service ->
                buildJsonObject {
                    put("version", 1)
                    put("command", command)
                    put("stop_when_error", false)
                    put("stop_when_success", false)
                    put("id", service)
                    put("serverID", id)
                    put("is_gofile", false)
                    put("path", "")
                }
            },
        )
        val url = "${server.trimEnd('/')}/Serv.php".toHttpUrlOrNull() ?: throw IOException("Invalid QuickConnect server $server")
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val text = http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("QuickConnect HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        val parsed: JsonElement = runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw IOException("Unexpected QuickConnect answer") }
        val objects = when (parsed) {
            is JsonArray -> parsed.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(parsed)
            else -> emptyList()
        }
        // Remember which request each answer belongs to; answers come in request order. If the
        // count does not match, the protocol is guessed from the port later.
        if (objects.size != services.size) return objects
        return objects.mapIndexed { i, obj -> JsonObject(obj + ("service_id" to JsonPrimitive(services[i]))) }
    }

    companion object {
        private val ID = Regex("^[A-Za-z0-9][A-Za-z0-9-]{0,62}$")

        /**
         * Extracts the QuickConnect ID from what the user typed: `myname`, `quickconnect.to/myname`,
         * `https://myname.quickconnect.to`, … Returns null for normal addresses.
         */
        fun idFrom(input: String): String? {
            val s = input.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
            if (s.isEmpty()) return null
            val lower = s.lowercase()
            return when {
                lower.startsWith("quickconnect.to/") -> s.substringAfter('/').substringBefore('/').takeIf { ID.matches(it) }
                lower.contains(".quickconnect.to") -> s.substringBefore('.').takeIf { ID.matches(it) }
                // A bare word without dots, colons or slashes cannot be a host name of a NAS on
                // the internet; "localhost"-like LAN names are rare and would need a port anyway.
                ID.matches(s) && !s.all { it.isDigit() } -> s
                else -> null
            }
        }
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.errno(): Int? = int("errno")
