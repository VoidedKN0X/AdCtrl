package com.vknxworks.adctrl.data.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBuilderTest {

    @Test
    fun blockAndAllowWholeDomain() {
        assertEquals("||example.com^", RuleBuilder.build(true, "example.com", RuleScope.HOST_AND_SUBDOMAINS, null))
        assertEquals("@@||example.com^\$important", RuleBuilder.build(false, "example.com", RuleScope.HOST_AND_SUBDOMAINS, null))
    }

    @Test
    fun scopesForASubdomain() {
        val host = "ads.tracker.example.co.uk"
        assertEquals("|ads.tracker.example.co.uk^", RuleBuilder.build(true, host, RuleScope.EXACT, null))
        assertEquals("||ads.tracker.example.co.uk^", RuleBuilder.build(true, host, RuleScope.HOST_AND_SUBDOMAINS, null))
        // The registrable domain comes from the public suffix list: co.uk is a suffix, so example.co.uk.
        assertEquals("||example.co.uk^", RuleBuilder.build(true, host, RuleScope.WHOLE_DOMAIN, null))
        assertEquals("@@||example.co.uk^\$important", RuleBuilder.build(false, host, RuleScope.WHOLE_DOMAIN, null))
        assertEquals("example.co.uk", RuleBuilder.registrable(host))
        assertEquals("example.com", RuleBuilder.registrable("a.b.c.example.com"))
    }

    @Test
    fun normalisesCaseAndTrailingDot() {
        assertEquals("||ads.example.com^", RuleBuilder.build(true, "  Ads.Example.COM.  ", RuleScope.HOST_AND_SUBDOMAINS, null))
    }

    @Test
    fun internationalNamesBecomePunycode() {
        assertEquals("xn--mnchen-3ya.de", RuleBuilder.normalizeHost("münchen.de"))
        assertEquals("||xn--mnchen-3ya.de^", RuleBuilder.build(true, "MÜNCHEN.de", RuleScope.HOST_AND_SUBDOMAINS, null))
        // Already punycode (as in the query log's ASCII name): kept as is.
        assertEquals("||xn--mnchen-3ya.de^", RuleBuilder.build(true, "xn--mnchen-3ya.de", RuleScope.HOST_AND_SUBDOMAINS, null))
        // The registrable part of an international subdomain.
        assertEquals("@@||xn--mnchen-3ya.de^\$important", RuleBuilder.build(false, "werbung.münchen.de", RuleScope.WHOLE_DOMAIN, null))
    }

    @Test
    fun wildcardsCoverTheDomainAndSubdomains() {
        assertTrue(RuleBuilder.isWildcard("*.example.com"))
        assertEquals("example.com", RuleBuilder.normalizeHost("*.example.com"))
        // "Only this host" can't apply to a wildcard: it becomes the domain and its subdomains.
        assertEquals("||example.com^", RuleBuilder.build(true, "*.example.com", RuleScope.EXACT, null))
        assertEquals("||example.com^", RuleBuilder.build(true, "*.*.example.com", RuleScope.HOST_AND_SUBDOMAINS, null))
        assertFalse(RuleBuilder.isWildcard("example.com"))
    }

    @Test
    fun ruleSyntaxInTheNameIsRefused() {
        // Anything that would change the meaning of the rule is refused rather than passed through.
        listOf(
            "ads.*.example.com",
            "example.com^",
            "||example.com",
            "example.com\$important",
            "example.com/path",
            "exa mple.com",
            "@@example.com",
            "example..com",
            "-bad.example.com",
            "",
            "*.",
            "a".repeat(64) + ".com",
        ).forEach { assertNull("should refuse '$it'", RuleBuilder.build(true, it, RuleScope.HOST_AND_SUBDOMAINS, null)) }
    }

    @Test
    fun underscoresAndDigitsAreFine() {
        assertEquals("||_dmarc.example.com^", RuleBuilder.build(true, "_dmarc.example.com", RuleScope.HOST_AND_SUBDOMAINS, null))
        assertEquals("|1.2.3.4^", RuleBuilder.build(true, "1.2.3.4", RuleScope.EXACT, null))
        // An IP address has no registrable domain: the whole-domain rule is the address itself.
        assertEquals("1.2.3.4", RuleBuilder.registrable("1.2.3.4"))
    }

    @Test
    fun clientOnlyRules() {
        assertEquals("||example.com^\$client=192.168.1.20", RuleBuilder.build(true, "example.com", RuleScope.HOST_AND_SUBDOMAINS, "192.168.1.20"))
        assertEquals("@@||example.com^\$important,client=192.168.1.0/24", RuleBuilder.build(false, "example.com", RuleScope.HOST_AND_SUBDOMAINS, "192.168.1.0/24"))
        assertEquals("||example.com^\$client=fd00::1", RuleBuilder.build(true, "example.com", RuleScope.HOST_AND_SUBDOMAINS, "fd00::1"))
        // A blank client means every client.
        assertEquals("||example.com^", RuleBuilder.build(true, "example.com", RuleScope.HOST_AND_SUBDOMAINS, "  "))
    }

    @Test
    fun clientNamesAreQuotedAndEscaped() {
        assertEquals("'Living room TV'", RuleBuilder.quoteClient("Living room TV"))
        assertEquals("'Axel\\'s phone'", RuleBuilder.quoteClient("Axel's phone"))
        assertEquals("'a\\, b'", RuleBuilder.quoteClient("a, b"))
        assertEquals("'back\\\\slash'", RuleBuilder.quoteClient("back\\slash"))
        assertEquals("'tv\$1'", RuleBuilder.quoteClient("tv\$1"))
        assertEquals(
            "@@||example.com^\$important,client='Axel\\'s phone'",
            RuleBuilder.build(false, "example.com", RuleScope.HOST_AND_SUBDOMAINS, "Axel's phone"),
        )
    }

    @Test
    fun allowRuleDetection() {
        assertTrue(RuleBuilder.isAllowRule("  @@||example.com^"))
        assertFalse(RuleBuilder.isAllowRule("||example.com^"))
    }
}
