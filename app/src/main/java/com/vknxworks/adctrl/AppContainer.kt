package com.vknxworks.adctrl

import android.content.Context
import com.vknxworks.adctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container; one per process. The HTTP client, server store and repositories
 * are added here as each part of the app is built.
 */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)
}

val Context.container: AppContainer
    get() = (applicationContext as AdCtrlApp).container
