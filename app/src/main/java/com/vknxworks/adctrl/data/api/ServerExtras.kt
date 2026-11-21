package com.vknxworks.adctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * AdGuard Home's own DHCP server and its encryption (TLS) status, as in openapi/openapi.yaml of
 * v0.107.79 (DhcpStatus, DhcpStaticLease, TlsConfig). Every field has a default: nothing missing crashes.
 */

@Serializable
data class DhcpV4(
    @SerialName("gateway_ip") val gatewayIp: String = "",
    @SerialName("subnet_mask") val subnetMask: String = "",
    @SerialName("range_start") val rangeStart: String = "",
    @SerialName("range_end") val rangeEnd: String = "",
    /** Seconds. */
    @SerialName("lease_duration") val leaseDuration: Long = 0,
)

@Serializable
data class DhcpV6(
    @SerialName("range_start") val rangeStart: String = "",
    @SerialName("lease_duration") val leaseDuration: Long = 0,
)

/** A lease: handed out ([expires] is an RFC 3339 time) or static ([expires] empty). */
@Serializable
data class DhcpLease(
    val mac: String = "",
    val ip: String = "",
    val hostname: String = "",
    val expires: String = "",
)

/** GET /control/dhcp/status in full. */
@Serializable
data class DhcpInfo(
    val enabled: Boolean = false,
    @SerialName("interface_name") val interfaceName: String = "",
    val v4: DhcpV4 = DhcpV4(),
    val v6: DhcpV6 = DhcpV6(),
    val leases: List<DhcpLease> = emptyList(),
    @SerialName("static_leases") val staticLeases: List<DhcpLease> = emptyList(),
)

/** The body of the three static-lease calls (DhcpStaticLease). */
@Serializable
data class DhcpStaticLease(val mac: String, val ip: String, val hostname: String)

suspend fun AghClient.dhcpInfo(): DhcpInfo = get("dhcp/status", DhcpInfo.serializer())

private fun leaseBody(l: DhcpStaticLease) = AghJson.encodeToJsonElement(DhcpStaticLease.serializer(), l)

suspend fun AghClient.addStaticLease(l: DhcpStaticLease) {
    post("dhcp/add_static_lease", leaseBody(l))
}

suspend fun AghClient.removeStaticLease(l: DhcpStaticLease) {
    post("dhcp/remove_static_lease", leaseBody(l))
}

/** Changes the address or name of the static lease with [l]'s MAC address (0.107.39 and newer). */
suspend fun AghClient.updateStaticLease(l: DhcpStaticLease) {
    post("dhcp/update_static_lease", leaseBody(l))
}

/**
 * GET /control/tls/status (TlsConfig). The certificate and key themselves are in the answer too
 * when they were pasted in; AdCtrl doesn't read them, so they never reach a screen or the phone's storage.
 */
@Serializable
data class TlsStatus(
    val enabled: Boolean = false,
    @SerialName("server_name") val serverName: String = "",
    @SerialName("force_https") val forceHttps: Boolean = false,
    @SerialName("port_https") val portHttps: Int = 0,
    @SerialName("port_dns_over_tls") val portDnsOverTls: Int = 0,
    @SerialName("port_dns_over_quic") val portDnsOverQuic: Int = 0,
    @SerialName("port_dnscrypt") val portDnsCrypt: Int = 0,
    @SerialName("serve_plain_dns") val servePlainDns: Boolean = true,
    @SerialName("valid_cert") val validCert: Boolean = false,
    @SerialName("valid_chain") val validChain: Boolean = false,
    @SerialName("valid_key") val validKey: Boolean = false,
    @SerialName("valid_pair") val validPair: Boolean = false,
    @SerialName("key_type") val keyType: String = "",
    val subject: String = "",
    val issuer: String = "",
    /** RFC 3339 times; empty without a certificate. */
    @SerialName("not_before") val notBefore: String = "",
    @SerialName("not_after") val notAfter: String = "",
    @SerialName("dns_names") val dnsNames: List<String> = emptyList(),
    @SerialName("warning_validation") val warningValidation: String = "",
) {
    /** Whether AdGuard Home has a certificate to report on at all. */
    val hasCertificate: Boolean get() = notAfter.isNotBlank() && !notAfter.startsWith("0001-")
}

suspend fun AghClient.tlsStatus(): TlsStatus = get("tls/status", TlsStatus.serializer())

/** How a certificate stands on a given day. */
enum class CertState { VALID, EXPIRES_SOON, EXPIRED, NOT_YET_VALID }

/** Days from [nowMs] until [notAfterMs] (negative once expired), counted in whole days. */
fun daysUntil(notAfterMs: Long, nowMs: Long): Long = Math.floorDiv(notAfterMs - nowMs, 86_400_000L)

/** The certificate's state at [nowMs]; "soon" is within [soonDays] days. */
fun certState(notBeforeMs: Long?, notAfterMs: Long, nowMs: Long, soonDays: Int = 21): CertState = when {
    notAfterMs <= nowMs -> CertState.EXPIRED
    notBeforeMs != null && notBeforeMs > nowMs -> CertState.NOT_YET_VALID
    daysUntil(notAfterMs, nowMs) < soonDays -> CertState.EXPIRES_SOON
    else -> CertState.VALID
}
