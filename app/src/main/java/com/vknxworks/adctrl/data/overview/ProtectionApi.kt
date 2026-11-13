package com.vknxworks.adctrl.data.overview

import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One request that changes protection: the path under /control/ and its JSON body. */
data class ProtectionRequest(val path: String, val body: JsonObject)

/**
 * Builds the request that pauses or resumes protection.
 *
 * - AdGuard Home 0.107.27 and newer: POST /control/protection {"enabled": false, "duration": ms}
 *   (no duration: off until turned back on), or {"enabled": true} to resume.
 * - Older servers, or a server that answers 404/405 there: POST /control/dns_config
 *   {"protection_enabled": …}. That can only switch protection on or off, so a pause for a set time
 *   is refused there instead of quietly becoming "until turned back on".
 */
object ProtectionApi {

    /** Pause for [pause] (null: resume), the modern way, or the old way with [legacy]. */
    fun request(pause: PauseDuration?, legacy: Boolean): ProtectionRequest {
        val enable = pause == null
        if (legacy) {
            if (pause?.ms != null) throw AghError.TimedPauseUnsupported()
            return ProtectionRequest("dns_config", buildJsonObject { put("protection_enabled", enable) })
        }
        return ProtectionRequest(
            "protection",
            buildJsonObject {
                put("enabled", enable)
                pause?.ms?.let { put("duration", it) }
            },
        )
    }

    /** Whether to go straight to the old way for this version. */
    fun legacyFor(version: AghVersion): Boolean = !version.protectionDuration

    /** The server has no /control/protection after all (a development build, a proxy that rewrites paths). */
    fun isMissingEndpoint(e: AghError): Boolean = e is AghError.Server && (e.code == 404 || e.code == 405)
}
