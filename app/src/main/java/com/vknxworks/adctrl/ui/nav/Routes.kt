package com.vknxworks.adctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object OverviewRoute
@Serializable data object QueriesRoute
@Serializable data object FiltersRoute
@Serializable data object ClientsRoute

// Account and settings, opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
