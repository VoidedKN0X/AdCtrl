package com.vknxworks.adctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object OverviewRoute
@Serializable data object QueriesRoute
@Serializable data object FiltersRoute
@Serializable data object ClientsRoute

/** A client: [key] is a persistent client's name, or a runtime client's IP address. */
@Serializable data class ClientRoute(val key: String, val persistent: Boolean)

/** Check host, optionally filled in with a domain and client. */
@Serializable data class CheckHostRoute(val host: String = "", val client: String = "")

/** Upstream DNS servers, bootstrap, fallback and private reverse DNS, upstream mode and cache. */
@Serializable data object DnsSettingsRoute

/** Query log and statistics: retention, and clearing them. */
@Serializable data object LogsStatsRoute

/** AdGuard Home's own DHCP server: leases and static leases. Only linked while that DHCP server is on. */
@Serializable data object DhcpRoute

/** Encryption status: HTTPS and encrypted DNS ports, and the certificate with its expiry. */
@Serializable data object EncryptionRoute

/** Copy settings from the active server to another saved server. */
@Serializable data object SyncRoute

// Account and settings, opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object NotificationsRoute
@Serializable data object ServersRoute
@Serializable data object AddServerRoute
@Serializable data class EditServerRoute(val id: String)
