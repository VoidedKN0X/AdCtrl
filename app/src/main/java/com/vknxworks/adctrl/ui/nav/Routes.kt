package com.vknxworks.adctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object OverviewRoute
@Serializable data object QueriesRoute
@Serializable data object FiltersRoute
@Serializable data object ClientsRoute

/** Check host, optionally filled in with a domain and client. */
@Serializable data class CheckHostRoute(val host: String = "", val client: String = "")

// Account and settings, opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object ServersRoute
@Serializable data object AddServerRoute
@Serializable data class EditServerRoute(val id: String)
