package com.vknxworks.adctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /control/status (ServerStatus in openapi.yaml). Every field has a default so missing ones never crash. */
@Serializable
data class ServerStatus(
    val version: String = "",
    @SerialName("dns_addresses") val dnsAddresses: List<String> = emptyList(),
    @SerialName("dns_port") val dnsPort: Int = 0,
    @SerialName("http_port") val httpPort: Int = 0,
    @SerialName("protection_enabled") val protectionEnabled: Boolean = true,
    /** Milliseconds left in a protection pause; 0 when not paused for a set time. */
    @SerialName("protection_disabled_duration") val protectionDisabledDuration: Long = 0,
    @SerialName("dhcp_available") val dhcpAvailable: Boolean = false,
    val running: Boolean = false,
    val language: String = "",
    /** When the web API server started, Unix time in milliseconds (0.107.70+). */
    @SerialName("start_time") val startTime: Double = 0.0,
)
