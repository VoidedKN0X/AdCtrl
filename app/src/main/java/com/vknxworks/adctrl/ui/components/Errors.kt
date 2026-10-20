package com.vknxworks.adctrl.ui.components

import android.content.Context
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion

/** A message for [e] the user can act on, from strings.xml. [host] names the server in the message. */
fun errorMessage(context: Context, e: Throwable, host: String): String {
    val base = when (e) {
        is AghError.Unreachable -> context.getString(R.string.error_unreachable, host)
        is AghError.UntrustedCertificate -> context.getString(R.string.error_untrusted_cert, host)
        is AghError.PinMismatch -> context.getString(R.string.error_pin_mismatch, host)
        is AghError.Unauthorized -> context.getString(R.string.error_bad_login)
        is AghError.Forbidden -> context.getString(R.string.error_forbidden)
        is AghError.NotAdGuard -> context.getString(R.string.error_not_adguard, host)
        is AghError.Redirect -> if (e.location.isNullOrBlank()) context.getString(R.string.error_redirect, host)
        else context.getString(R.string.error_redirect_to, host, e.location)
        is AghError.TooOld -> context.getString(R.string.error_too_old, e.version, AghVersion.MINIMUM.raw)
        is AghError.CleartextRefused -> context.getString(R.string.error_cleartext, host)
        is AghError.BlockedHost -> context.getString(R.string.error_blocked_host)
        is AghError.ReadOnly -> context.getString(R.string.error_read_only)
        is AghError.Cancelled -> context.getString(R.string.error_cancelled)
        is AghError.Server -> context.getString(R.string.error_server, e.code)
        else -> context.getString(R.string.error_generic)
    }
    // AdGuard Home's own wording helps for refused and failed requests; network errors' detail is noise.
    val detail = when (e) {
        is AghError.Forbidden, is AghError.Server -> e.detail
        else -> null
    }
    return if (detail.isNullOrBlank()) base else context.getString(R.string.error_with_detail, base, detail)
}
