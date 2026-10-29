package com.calmyjane.jaymusic.core.synology

import com.calmyjane.jaymusic.core.synology.SynologyException.Companion.API_AUTH
import com.calmyjane.jaymusic.core.synology.SynologyException.Companion.API_DOWNLOAD
import com.calmyjane.jaymusic.core.synology.SynologyException.Companion.API_INFO
import com.calmyjane.jaymusic.core.synology.SynologyException.Companion.API_LIST
import com.calmyjane.jaymusic.core.synology.SynologyException.Companion.CODE_API_MISSING
import com.calmyjane.jaymusic.core.synology.SynologyException.Companion.CODE_BAD_RESPONSE
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal client for the documented Synology DSM File Station Web API.
 *
 * Only what a music player needs: discovering the API, logging in (with optional 2-factor code
 * and device token), listing folders and building download/stream URLs.
 */
class SynologyClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Logs in to the NAS at [baseUrl] (e.g. `https://nas.example.com:5001`).
     *
     * @param otpCode 2-factor code, only needed when the server asks for it (see
     *   [SynologyException.isOtpRequired]). When given, a device token is requested so later
     *   logins with [deviceId] do not need a code again.
     */
    suspend fun login(
        baseUrl: String,
        account: String,
        password: String,
        deviceName: String,
        otpCode: String? = null,
        deviceId: String? = null,
    ): Session {
        val base = normalizeBaseUrl(baseUrl)
            ?: throw IllegalArgumentException("Invalid server address: $baseUrl")
        val apis = queryApis(base)
        val auth = apis[API_AUTH] ?: throw SynologyException(CODE_API_MISSING, API_AUTH)
        if (!apis.containsKey(API_LIST) || !apis.containsKey(API_DOWNLOAD)) {
            throw SynologyException(CODE_API_MISSING, API_LIST)
        }
        val version = auth.pick(6)
        val form = FormBody.Builder()
            .add("api", API_AUTH)
            .add("version", version.toString())
            .add("method", "login")
            .add("account", account)
            .add("passwd", password)
            .add("session", "FileStation")
            .add("format", "sid")
        if (!otpCode.isNullOrBlank()) {
            form.add("otp_code", otpCode.trim())
            if (version >= 6) {
                form.add("enable_device_token", "yes")
                form.add("device_name", deviceName)
            }
        } else if (!deviceId.isNullOrBlank() && version >= 6) {
            form.add("device_name", deviceName)
            form.add("device_id", deviceId)
        }
        val request = Request.Builder().url(base.resolveApi(auth.path)).post(form.build()).build()
        val data = execute(request, API_AUTH).jsonObject
        val sid = data.string("sid") ?: throw SynologyException(CODE_BAD_RESPONSE, API_AUTH)
        val did = data.string("did")?.takeIf { it.isNotBlank() } ?: deviceId
        return Session(base, sid, did, apis)
    }

    suspend fun logout(session: Session) {
        val auth = session.apis[API_AUTH] ?: return
        val url = session.apiUrl(auth.path)
            .addQueryParameter("api", API_AUTH)
            .addQueryParameter("version", auth.pick(6).toString())
            .addQueryParameter("method", "logout")
            .addQueryParameter("session", "FileStation")
            .addQueryParameter("_sid", session.sid)
            .build()
        runCatching { execute(Request.Builder().url(url).build(), API_AUTH) }
    }

    /** Lists the shared folders visible to the logged in user. */
    suspend fun listShares(session: Session): List<RemoteEntry> {
        val result = mutableListOf<RemoteEntry>()
        paginate { offset ->
            val data = callList(session, "list_share", offset, emptyMap())
            data.array("shares").mapTo(result) { parseEntry(it.jsonObject, forceDir = true) }
            data.int("total") ?: 0
        }
        return result
    }

    /** Lists all entries of [folderPath] (e.g. `/music/Artist`), following pagination. */
    suspend fun listFolder(session: Session, folderPath: String): List<RemoteEntry> {
        val result = mutableListOf<RemoteEntry>()
        paginate { offset ->
            val data = callList(
                session, "list", offset,
                mapOf(
                    "folder_path" to folderPath,
                    "additional" to """["size","time"]""",
                    "filetype" to "all",
                ),
            )
            data.array("files").mapTo(result) { parseEntry(it.jsonObject, forceDir = false) }
            data.int("total") ?: 0
        }
        return result
    }

    /**
     * URL that streams the file at [path]. The server honours HTTP range requests, so it can be
     * used directly for streaming and seeking.
     */
    fun downloadUrl(session: Session, path: String): HttpUrl {
        val api = session.apis[API_DOWNLOAD] ?: throw SynologyException(CODE_API_MISSING, API_DOWNLOAD)
        val version = api.pick(2)
        val pathParam = if (version >= 2) json.encodeToString(JsonArray.serializer(), JsonArray(listOf(JsonPrimitive(path)))) else path
        return session.apiUrl(api.path)
            .addQueryParameter("api", API_DOWNLOAD)
            .addQueryParameter("version", version.toString())
            .addQueryParameter("method", "download")
            .addQueryParameter("path", pathParam)
            .addQueryParameter("mode", "download")
            .addQueryParameter("_sid", session.sid)
            .build()
    }

    private suspend fun queryApis(base: HttpUrl): Map<String, ApiInfo> {
        val url = base.resolveApi("query.cgi").newBuilder()
            .addQueryParameter("api", API_INFO)
            .addQueryParameter("version", "1")
            .addQueryParameter("method", "query")
            .addQueryParameter("query", "$API_AUTH,$API_LIST,$API_DOWNLOAD")
            .build()
        val data = execute(Request.Builder().url(url).build(), API_INFO).jsonObject
        return data.mapNotNull { (name, value) ->
            val obj = value as? JsonObject ?: return@mapNotNull null
            val path = obj.string("path") ?: return@mapNotNull null
            name to ApiInfo(path, obj.int("minVersion") ?: 1, obj.int("maxVersion") ?: 1)
        }.toMap()
    }

    private suspend fun callList(
        session: Session,
        method: String,
        offset: Int,
        params: Map<String, String>,
    ): JsonObject {
        val api = session.apis[API_LIST] ?: throw SynologyException(CODE_API_MISSING, API_LIST)
        val url = session.apiUrl(api.path)
            .addQueryParameter("api", API_LIST)
            .addQueryParameter("version", api.pick(2).toString())
            .addQueryParameter("method", method)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .addQueryParameter("_sid", session.sid)
            .build()
        return execute(Request.Builder().url(url).build(), API_LIST).jsonObject
    }

    private suspend fun paginate(page: suspend (offset: Int) -> Int) {
        var offset = 0
        while (true) {
            val total = page(offset)
            offset += PAGE_SIZE
            if (offset >= total) return
        }
    }

    private fun parseEntry(obj: JsonObject, forceDir: Boolean): RemoteEntry {
        val additional = obj["additional"] as? JsonObject
        val time = additional?.get("time") as? JsonObject
        return RemoteEntry(
            path = obj.string("path").orEmpty(),
            name = obj.string("name").orEmpty(),
            isDir = forceDir || obj.bool("isdir") == true,
            size = additional?.long("size") ?: 0,
            modified = time?.long("mtime") ?: 0,
        )
    }

    /** Executes [request] and returns the `data` member of a successful API answer. */
    private suspend fun execute(request: Request, api: String): JsonElement {
        val body = http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from ${request.url.host}")
            response.body?.string() ?: throw SynologyException(CODE_BAD_RESPONSE, api)
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw SynologyException(CODE_BAD_RESPONSE, api) }
        if (root.bool("success") != true) {
            val code = (root["error"] as? JsonObject)?.int("code") ?: CODE_BAD_RESPONSE
            throw SynologyException(code, api)
        }
        return root["data"] ?: JsonObject(emptyMap())
    }

    private fun Session.apiUrl(path: String): HttpUrl.Builder = baseUrl.resolveApi(path).newBuilder()

    companion object {
        private const val PAGE_SIZE = 1000

        /**
         * Accepts what users type: `nas.local:5000`, `https://example.com:5001/`, … and returns
         * the server root URL, or null if it cannot be parsed. Plain host names default to http.
         */
        fun normalizeBaseUrl(input: String): HttpUrl? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            return url.newBuilder().encodedPath("/").query(null).fragment(null).build()
        }

        private fun HttpUrl.resolveApi(path: String): HttpUrl =
            newBuilder().encodedPath("/webapi/${path.trimStart('/')}").build()
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()

/** Suspends until the call completes; cancelling the coroutine cancels the HTTP call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
}
