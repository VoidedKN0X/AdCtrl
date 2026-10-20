package com.vknxworks.adctrl.data.api

import com.vknxworks.adctrl.data.server.PrivateOnlyDns
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.data.server.ServerSecrets
import com.vknxworks.adctrl.data.server.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/** Lenient JSON for AdGuard Home responses: unknown fields ignored, nulls and bad values fall back to defaults. */
val AghJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * OkHttp client for one server: normal certificate checks, or only the pinned certificate when the
 * user confirmed a self-signed one (never "trust all"). Plain-HTTP servers only resolve to private
 * addresses ([PrivateOnlyDns]).
 */
fun httpFor(base: OkHttpClient, url: HttpUrl, certPin: String): OkHttpClient {
    if (!url.isHttps) return base.newBuilder().dns(PrivateOnlyDns).build()
    if (certPin.isBlank()) return base
    val tm = Tls.PinnedTrustManager(certPin, Tls.systemTrustManager())
    val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), null) }
    return base.newBuilder()
        .sslSocketFactory(ctx.socketFactory, tm)
        .hostnameVerifier(Tls.pinnedHostnameVerifier(certPin, url.host))
        .build()
}

/**
 * AdGuard Home REST client: <server>/control/…, signed in with HTTP Basic auth on every request.
 * Never logs requests, responses, the password or addresses with credentials.
 */
class AghClient(
    val server: Server,
    base: OkHttpClient,
    private val secrets: ServerSecrets,
    /**
     * Whether the server is in read-only mode right now. Read on every request (not copied from
     * [server] once), so switching read-only on also stops clients a screen is still holding.
     */
    private val isReadOnly: () -> Boolean = { server.readOnly },
) {
    private val baseUrl: HttpUrl = server.url.toHttpUrl()
    val http: OkHttpClient = httpFor(base, baseUrl, server.certPin)
    private val auth = Credentials.basic(server.username, secrets.password, Charsets.UTF_8)

    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val b = baseUrl.newBuilder().addPathSegment("control")
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { b.addPathSegment(it) }
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement = call("GET", path, query, null)

    suspend fun <T> get(path: String, strategy: DeserializationStrategy<T>, query: Map<String, String> = emptyMap()): T =
        decode(strategy, get(path, query))

    suspend fun post(path: String, body: JsonElement? = null): JsonElement = call("POST", path, emptyMap(), body)

    suspend fun put(path: String, body: JsonElement? = null): JsonElement = call("PUT", path, emptyMap(), body)

    /** GET /control/status: also proves the address is AdGuard Home and the sign-in works. */
    suspend fun status(): ServerStatus {
        val s = try {
            get("status", ServerStatus.serializer())
        } catch (e: AghError.Server) {
            if (e.code == 404) throw AghError.NotAdGuard() else throw e
        }
        if (s.version.isBlank()) throw AghError.NotAdGuard()
        return s
    }

    private fun <T> decode(strategy: DeserializationStrategy<T>, element: JsonElement): T = try {
        AghJson.decodeFromJsonElement(strategy, element)
    } catch (e: SerializationException) {
        throw AghError.NotAdGuard()
    } catch (e: IllegalArgumentException) {
        throw AghError.NotAdGuard()
    }

    private suspend fun call(method: String, path: String, query: Map<String, String>, body: JsonElement?): JsonElement {
        // Read-only mode is enforced here, for every request that could change something, not only by hiding buttons.
        if (method != "GET" && isReadOnly()) throw AghError.ReadOnly()
        if (method != "GET" && !WriteGuard.check()) throw AghError.Cancelled()
        return withContext(Dispatchers.IO) {
            val requestBody = when {
                method == "GET" -> null
                body == null -> ByteArray(0).toRequestBody(null)
                else -> AghJson.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_TYPE)
            }
            val request = Request.Builder()
                .url(url(path, query))
                .method(method, requestBody)
                .header("Authorization", auth)
                .header("Accept", "application/json")
                .build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw AghError.fromIo(e)
            }
            response.use { parse(it) }
        }
    }

    private fun parse(r: Response): JsonElement {
        val text = try {
            r.body?.string().orEmpty()
        } catch (e: IOException) {
            throw AghError.fromIo(e)
        }
        when {
            r.code == 401 -> throw AghError.Unauthorized()
            r.code == 403 -> throw AghError.Forbidden(detailOf(text))
            r.code in 300..399 -> throw AghError.Redirect(r.header("Location")?.let { loc -> runCatching { r.request.url.resolve(loc)?.host }.getOrNull() })
            !r.isSuccessful -> throw AghError.Server(r.code, detailOf(text))
        }
        if (text.isBlank()) return JsonNull
        // A web page where JSON was expected: a reverse proxy or another app answered.
        if (text.trimStart().startsWith("<")) throw AghError.NotAdGuard()
        return try {
            AghJson.parseToJsonElement(text)
        } catch (_: SerializationException) {
            // Some actions answer with plain text ("OK"); that's a success, not data.
            JsonNull
        }
    }

    /** AdGuard Home's own error text, kept short. HTML error pages from a proxy are dropped. */
    private fun detailOf(text: String): String? =
        text.trim().takeIf { it.isNotEmpty() && !it.startsWith("<") }?.take(300)
}
