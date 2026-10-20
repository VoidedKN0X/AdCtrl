package com.vknxworks.adctrl.data.filters

/**
 * Well-known blocklists to pick from when adding a list. Taken from AdGuard Home's own vetted list
 * (client/src/helpers/filters/filters.ts, served from AdGuard's HostlistsRegistry) and shipped with
 * the app: nothing is fetched to show this list.
 */
object KnownLists {

    enum class Category { GENERAL, SECURITY, OTHER }

    data class Known(val name: String, val url: String, val category: Category)

    private const val BASE = "https://adguardteam.github.io/HostlistsRegistry/assets/"

    private fun g(name: String, n: Int) = Known(name, "${BASE}filter_$n.txt", Category.GENERAL)
    private fun s(name: String, n: Int) = Known(name, "${BASE}filter_$n.txt", Category.SECURITY)
    private fun o(name: String, n: Int) = Known(name, "${BASE}filter_$n.txt", Category.OTHER)

    val all: List<Known> = listOf(
        g("AdGuard DNS filter", 1),
        g("AdGuard DNS Popup Hosts filter", 59),
        g("HaGeZi's Normal Blocklist", 34),
        g("HaGeZi's Pro Blocklist", 48),
        g("HaGeZi's Pro++ Blocklist", 51),
        g("HaGeZi's Ultimate Blocklist", 49),
        g("OISD Blocklist Small", 5),
        g("OISD Blocklist Big", 27),
        g("1Hosts (Lite)", 24),
        g("1Hosts (Xtra)", 70),
        g("Steven Black's List", 33),
        g("Peter Lowe's Blocklist", 3),
        g("Dan Pollock's List", 4),
        g("ShadowWhisperer Tracking List", 69),
        g("AWAvenue Ads Rule", 53),
        s("Dandelion Sprout's Anti-Malware List", 12),
        s("HaGeZi's Threat Intelligence Feeds", 44),
        s("HaGeZi's Badware Hoster Blocklist", 55),
        s("HaGeZi's DynDNS Blocklist", 54),
        s("HaGeZi's The World's Most Abused TLDs", 56),
        s("HaGeZi's URL Shortener Blocklist", 68),
        s("HaGeZi's DNS Rebind Protection", 71),
        s("HaGeZi's Encrypted DNS/VPN/TOR/Proxy Bypass", 52),
        s("Malicious URL Blocklist (URLHaus)", 11),
        s("Phishing Army", 18),
        s("Phishing URL Blocklist (PhishTank and OpenPhish)", 30),
        s("Scam Blocklist by DurableNapkin", 10),
        s("NoCoin Filter List", 8),
        s("Stalkerware Indicators List", 31),
        s("ShadowWhisperer's Malware List", 42),
        s("The Big List of Hacked Malware Web Sites", 9),
        s("uBlock₀ filters – Badware risks", 50),
        o("HaGeZi's OPPO & Realme Tracker Blocklist", 66),
        o("HaGeZi's Samsung Tracker Blocklist", 61),
        o("HaGeZi's Xiaomi Tracker Blocklist", 60),
        o("HaGeZi's Vivo Tracker Blocklist", 65),
        o("HaGeZi's Apple Tracker Blocklist", 67),
        o("HaGeZi's Windows/Office Tracker Blocklist", 63),
        o("Perflyst and Dandelion Sprout's Smart-TV Blocklist", 7),
        o("Dandelion Sprout's Game Console Adblock List", 6),
        o("Dandelion Sprout's Anti Push Notifications", 39),
        o("HaGeZi's Gambling Blocklist", 47),
        o("ShadowWhisperer's Dating List", 57),
        o("HaGeZi's Anti-Piracy Blocklist", 46),
        o("No Google", 37),
    )
}
