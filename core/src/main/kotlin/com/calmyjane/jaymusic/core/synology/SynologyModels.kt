package com.calmyjane.jaymusic.core.synology

import okhttp3.HttpUrl

/** A file or folder on the NAS as reported by File Station. */
data class RemoteEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long = 0,
    /** Last modification time in epoch seconds, 0 if unknown. */
    val modified: Long = 0,
)

/** Location and supported version range of one Web API, as reported by SYNO.API.Info. */
data class ApiInfo(val path: String, val minVersion: Int, val maxVersion: Int) {
    fun pick(preferred: Int): Int = preferred.coerceIn(minVersion, maxVersion)
}

/** An authenticated File Station session. */
data class Session(
    val baseUrl: HttpUrl,
    val sid: String,
    /** Device token issued after a successful 2-factor login; lets later logins skip the OTP. */
    val deviceId: String?,
    val apis: Map<String, ApiInfo>,
)

class SynologyException(
    val code: Int,
    val api: String,
    message: String = describe(api, code),
) : Exception(message) {

    /** The session id expired or was invalidated; logging in again fixes it. */
    val isSessionError: Boolean get() = code in SESSION_ERRORS

    /** Login needs a 2-factor (OTP) code. */
    val isOtpRequired: Boolean get() = api == API_AUTH && code == 403

    /** The OTP code supplied was wrong. */
    val isOtpInvalid: Boolean get() = api == API_AUTH && code == 404

    val isBadCredentials: Boolean get() = api == API_AUTH && code == 400

    companion object {
        const val API_INFO = "SYNO.API.Info"
        const val API_AUTH = "SYNO.API.Auth"
        const val API_LIST = "SYNO.FileStation.List"
        const val API_DOWNLOAD = "SYNO.FileStation.Download"

        /** Codes returned by the client itself when the server answer is unusable. */
        const val CODE_BAD_RESPONSE = -1
        const val CODE_API_MISSING = -2

        private val SESSION_ERRORS = setOf(105, 106, 107, 119)

        private val COMMON = mapOf(
            100 to "Unknown error",
            101 to "No parameter of API, method or version",
            102 to "The requested API does not exist",
            103 to "The requested method does not exist",
            104 to "The requested version does not support the functionality",
            105 to "The logged in session does not have permission",
            106 to "Session timeout",
            107 to "Session interrupted by duplicate login",
            119 to "SID not found",
        )

        private val AUTH = mapOf(
            400 to "Wrong user name or password",
            401 to "Account disabled",
            402 to "Permission denied",
            403 to "2-factor authentication code required",
            404 to "Wrong 2-factor authentication code",
            406 to "2-factor authentication must be enabled for this account",
            407 to "Blocked IP address (too many failed logins)",
            408 to "Password expired and must be changed",
            409 to "Password expired",
            410 to "Password must be changed",
        )

        private val FILE_STATION = mapOf(
            400 to "Invalid file operation parameter",
            401 to "Unknown file operation error",
            402 to "System is too busy",
            407 to "Operation not permitted",
            408 to "No such file or directory",
            414 to "File already exists",
            415 to "Disk quota exceeded",
            416 to "No space left on device",
            418 to "Illegal name or path",
        )

        fun describe(api: String, code: Int): String {
            val specific = when {
                code == CODE_BAD_RESPONSE -> "Unexpected answer from the server"
                code == CODE_API_MISSING -> "File Station is not available on this server"
                api == API_AUTH -> AUTH[code]
                api.startsWith("SYNO.FileStation") -> FILE_STATION[code]
                else -> null
            }
            return specific ?: COMMON[code] ?: "Synology error $code"
        }
    }
}
