package com.vknxworks.adctrl.data.api

import com.vknxworks.adctrl.data.overview.PauseDuration
import com.vknxworks.adctrl.data.overview.ProtectionApi
import com.vknxworks.adctrl.data.rules.RuleEdit
import com.vknxworks.adctrl.data.rules.RulesApi
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.data.server.ServerSecrets
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The requests AdCtrl sends for the new block/allow and pause features, against a fake AdGuard Home:
 * an OkHttp interceptor that records each request and answers with recorded responses, so nothing
 * leaves the computer.
 */
class ApiRequestsTest {

    private class Fake(private val answer: (Request) -> Pair<Int, String>) : Interceptor {
        val requests = mutableListOf<Pair<Request, String?>>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            val body = r.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
            requests += r to body
            val (code, text) = answer(r)
            return Response.Builder()
                .request(r)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(text.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private val server = Server(id = "s1", name = "Home", url = "https://adguard.example.test", username = "admin", version = "v0.107.79")

    private fun client(fake: Fake, s: Server = server) =
        AghClient(s, OkHttpClient.Builder().addInterceptor(fake).build(), ServerSecrets("secret"))

    @Test
    fun pauseBodies() {
        val ten = ProtectionApi.request(PauseDuration.M10, legacy = false)
        assertEquals("protection", ten.path)
        assertEquals("""{"enabled":false,"duration":600000}""", ten.body.toString())
        assertEquals("""{"enabled":false,"duration":60000}""", ProtectionApi.request(PauseDuration.M1, false).body.toString())
        assertEquals("""{"enabled":false,"duration":3600000}""", ProtectionApi.request(PauseDuration.H1, false).body.toString())
        assertEquals("""{"enabled":false}""", ProtectionApi.request(PauseDuration.UNTIL_ON, false).body.toString())
        assertEquals("""{"enabled":true}""", ProtectionApi.request(null, false).body.toString())
    }

    @Test
    fun legacyFallbackOnlySwitches() {
        val off = ProtectionApi.request(PauseDuration.UNTIL_ON, legacy = true)
        assertEquals("dns_config", off.path)
        assertEquals("""{"protection_enabled":false}""", off.body.toString())
        assertEquals("""{"protection_enabled":true}""", ProtectionApi.request(null, true).body.toString())
        try {
            ProtectionApi.request(PauseDuration.M10, legacy = true)
            fail("a timed pause must not silently become 'until turned back on'")
        } catch (_: AghError.TimedPauseUnsupported) {
        }
        assertTrue(ProtectionApi.legacyFor(AghVersion.parse("v0.107.26")))
        assertFalse(ProtectionApi.legacyFor(AghVersion.parse("v0.107.27")))
        assertFalse(ProtectionApi.legacyFor(AghVersion.parse("v0.108.0-b.5")))
        // A development build without a version number is treated as new.
        assertFalse(ProtectionApi.legacyFor(AghVersion.parse("dev")))
        assertTrue(ProtectionApi.isMissingEndpoint(AghError.Server(404, null)))
        assertTrue(ProtectionApi.isMissingEndpoint(AghError.Server(405, null)))
        assertFalse(ProtectionApi.isMissingEndpoint(AghError.Server(500, null)))
        assertFalse(ProtectionApi.isMissingEndpoint(AghError.Unauthorized()))
    }

    @Test
    fun pauseRequestOnTheWire() = runBlocking {
        val fake = Fake { 200 to "OK" }
        val req = ProtectionApi.request(PauseDuration.H1, legacy = false)
        client(fake).post(req.path, req.body)
        val (r, body) = fake.requests.single()
        assertEquals("POST", r.method)
        assertEquals("https://adguard.example.test/control/protection", r.url.toString())
        // AdGuard Home refuses anything but exactly application/json.
        assertEquals("application/json", r.body?.contentType().toString())
        assertEquals("""{"enabled":false,"duration":3600000}""", body)
        assertTrue(r.header("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun setRulesBodyKeepsEveryLineInOrder() {
        val rules = listOf("! comment", "||a.com^", "@@||b.com^\$important,client='Axel\\'s phone'", "")
        assertEquals(
            """{"rules":["! comment","||a.com^","@@||b.com^${'$'}important,client='Axel\\'s phone'",""]}""",
            RulesApi.setRulesBody(rules).toString(),
        )
    }

    @Test
    fun blockFromRecordedResponses() = runBlocking {
        // Recorded GET /control/filtering/status (shortened) and GET /control/filtering/check_host answers.
        val status = """
            {"enabled":true,"interval":24,"filters":[{"id":1,"name":"AdGuard DNS filter","url":"https://example.test/f.txt","enabled":true,"rules_count":50000,"last_updated":"2026-11-12T10:00:00Z"}],
             "whitelist_filters":null,"user_rules":["! My rules","@@||tracker.example.com^${'$'}important","||ads.example.org^"]}
        """.trimIndent()
        val checkHost = """{"reason":"NotFilteredWhiteList","rules":[{"filter_list_id":0,"text":"@@||tracker.example.com^${'$'}important"}],"service_name":"","cname":"","ip_addrs":null}"""
        val fake = Fake { r ->
            when (r.url.encodedPath) {
                "/control/filtering/status" -> 200 to status
                "/control/filtering/check_host" -> 200 to checkHost
                else -> 200 to "OK"
            }
        }
        val api = client(fake)

        val parsed = api.get("filtering/status", FilteringStatus.serializer())
        val check = api.get("filtering/check_host", CheckHostResult.serializer(), mapOf("name" to "tracker.example.com"))
        assertEquals("tracker.example.com", fake.requests[1].first.url.queryParameter("name"))
        assertEquals("@@||tracker.example.com^\$important", check.rules.single().text)

        // Blocking finds the allow rule as the opposite, removes it and appends the block rule.
        val newRule = "||tracker.example.com^"
        assertEquals(listOf("@@||tracker.example.com^\$important"), RuleEdit.opposites(parsed.userRules, newRule))
        val (kept, _) = RuleEdit.remove(parsed.userRules, RuleEdit.opposites(parsed.userRules, newRule))
        val updated = RuleEdit.append(kept, newRule)!!
        api.post("filtering/set_rules", RulesApi.setRulesBody(updated))
        val (r, body) = fake.requests.last()
        assertEquals("https://adguard.example.test/control/filtering/set_rules", r.url.toString())
        assertEquals("""{"rules":["! My rules","||ads.example.org^","||tracker.example.com^"]}""", body)
        assertNull(RuleEdit.append(updated, newRule))
    }

    @Test
    fun readOnlyServersSendNothing() = runBlocking {
        val fake = Fake { 200 to "OK" }
        try {
            client(fake, server.copy(readOnly = true)).post("protection", ProtectionApi.request(null, false).body)
            fail("read-only must refuse")
        } catch (_: AghError.ReadOnly) {
        }
        assertTrue(fake.requests.isEmpty())
    }
}
