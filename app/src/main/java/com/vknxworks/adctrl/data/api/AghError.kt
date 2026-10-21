package com.vknxworks.adctrl.data.api

import com.vknxworks.adctrl.data.server.BlockedHostException
import com.vknxworks.adctrl.data.server.CleartextRefusedException
import com.vknxworks.adctrl.data.server.Tls
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Everything that can go wrong talking to AdGuard Home, sorted into what the user can act on.
 * The UI turns these into messages from strings.xml; [detail] is AdGuard Home's own wording when it gave one.
 */
sealed class AghError(val detail: String? = null, cause: Throwable? = null) : IOException(detail, cause) {
    /** No route, DNS failure, refused or timed out. */
    class Unreachable(cause: Throwable?) : AghError(null, cause)

    /** The certificate isn't trusted by the phone and hasn't been pinned. */
    class UntrustedCertificate(cause: Throwable?) : AghError(null, cause)

    /** The server now presents a different certificate from the pinned one. */
    class PinMismatch(cause: Throwable?) : AghError(null, cause)

    /** 401: wrong username or password, or sign-ins are temporarily blocked after failed attempts. */
    class Unauthorized : AghError(null)

    /** 403: signed in, but AdGuard Home refused this request. */
    class Forbidden(detail: String?) : AghError(detail)

    /** The address answered, but not like AdGuard Home's API (a reverse proxy page, another app). */
    class NotAdGuard : AghError(null)

    /** The server answered with a redirect (for example to HTTPS); AdCtrl never follows one. */
    class Redirect(val location: String?) : AghError(null)

    /** AdGuard Home is older than AdCtrl supports. */
    class TooOld(val version: String) : AghError(null)

    /** Plain HTTP to an address outside the user's own network, or without the server's HTTP switch on. */
    class CleartextRefused : AghError(null)

    /** A request to a host that isn't a configured server; stopped before it was sent. */
    class BlockedHost : AghError(null)

    /** The server is in read-only mode, so AdCtrl refused to send a change. Raised before anything is sent. */
    class ReadOnly : AghError(null)

    /** The change wasn't confirmed with the fingerprint or app PIN ("Confirm changes"); nothing was sent. */
    class Cancelled : AghError(null)

    /** Any other error status from AdGuard Home (bad request, internal error, …). */
    class Server(val code: Int, detail: String?) : AghError(detail)

    companion object {
        private fun causes(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause.takeIf { c -> c !== it } }

        /** Sorts a network-level failure from OkHttp. */
        fun fromIo(e: IOException): AghError = when {
            e is AghError -> e
            causes(e).any { it is BlockedHostException } -> BlockedHost()
            causes(e).any { it is CleartextRefusedException } -> CleartextRefused()
            Tls.isPinMismatch(e) -> PinMismatch(e)
            Tls.isCertificateError(e) -> UntrustedCertificate(e)
            e is SSLHandshakeException -> UntrustedCertificate(e)
            e is UnknownHostException || e is ConnectException || e is NoRouteToHostException || e is SocketTimeoutException -> Unreachable(e)
            else -> Unreachable(e)
        }
    }
}
