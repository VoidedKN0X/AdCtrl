package com.vknxworks.adctrl.data.api

/**
 * An AdGuard Home version as reported by GET /control/status ("v0.107.79"), and the API features
 * that version has, taken from openapi/CHANGELOG.md in the AdGuard Home repository.
 */
data class AghVersion(val raw: String, val major: Int, val minor: Int, val patch: Int, val known: Boolean) : Comparable<AghVersion> {

    override fun compareTo(other: AghVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    private fun atLeast(patch: Int) = !known || this >= of(0, 107, patch)

    /** Supported at all: pausing protection for a set time (0.107.27) and blocked services with schedules (0.107.37). */
    val supported: Boolean get() = atLeast(37)

    /** POST /control/clients/search replaces GET /control/clients/find (0.107.56). */
    val clientsSearch: Boolean get() = atLeast(56)

    /** GET /control/filtering/check_host takes client and qtype (0.107.58). */
    val checkHostClient: Boolean get() = atLeast(58)

    /** "cache_enabled" in dns_info and dns_config (0.107.64). */
    val cacheToggle: Boolean get() = atLeast(64)

    /** Rewrites can be switched on and off, globally and one by one; blocked services have groups (0.107.68). */
    val rewriteToggle: Boolean get() = atLeast(68)

    /** GET /control/stats takes "recent", a shorter lookback than the retention period (0.107.72). */
    val statsRecent: Boolean get() = atLeast(72)

    /** GET /control/querylog filters with "reason"; "response_status" is deprecated (0.107.77). */
    val querylogReason: Boolean get() = atLeast(77)

    override fun toString(): String = raw

    companion object {
        /** The oldest version AdCtrl works with. */
        val MINIMUM = of(0, 107, 37)

        private fun of(major: Int, minor: Int, patch: Int) = AghVersion("v$major.$minor.$patch", major, minor, patch, true)

        private val PATTERN = Regex("""(\d+)\.(\d+)\.(\d+)""")

        /**
         * Parses "v0.107.79" (and pre-releases like "v0.108.0-b.5"). A version that can't be read,
         * such as a development build, is treated as new enough for every feature.
         */
        fun parse(raw: String): AghVersion {
            val m = PATTERN.find(raw) ?: return AghVersion(raw, 0, 0, 0, false)
            val (a, b, c) = m.destructured
            return AghVersion(raw, a.toInt(), b.toInt(), c.toInt(), true)
        }
    }
}
