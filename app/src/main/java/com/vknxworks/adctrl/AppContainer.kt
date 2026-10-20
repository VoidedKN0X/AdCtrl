package com.vknxworks.adctrl

import android.content.Context
import com.vknxworks.adctrl.data.clients.ClientsRepository
import com.vknxworks.adctrl.data.filters.FiltersRepository
import com.vknxworks.adctrl.data.overview.OverviewRepository
import com.vknxworks.adctrl.data.queries.QueriesRepository
import com.vknxworks.adctrl.data.rules.UserRules
import com.vknxworks.adctrl.data.server.HostAllowlist
import com.vknxworks.adctrl.data.server.ServerStore
import com.vknxworks.adctrl.data.server.Sessions
import com.vknxworks.adctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)

    val serverStore = ServerStore(context)

    /** The saved servers (and the one being set up) are the only hosts AdCtrl may connect to. */
    val allowlist = HostAllowlist(serverStore)

    /**
     * Shared HTTP client. Each server gets a copy with its own certificate pin (see httpFor), so a
     * pinned certificate is only ever accepted for the server it belongs to. Redirects are not
     * followed: AdGuard Home's API never needs them, and following one could send the password to
     * another host. No cookies are kept and nothing is cached on disk.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(allowlist.interceptor)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AdCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .build()

    val sessions = Sessions(serverStore, http, appScope)
    val overview = OverviewRepository(context, sessions, appScope)
    val queries = QueriesRepository(sessions, appScope)
    val userRules = UserRules(sessions)
    val filters = FiltersRepository(sessions, appScope)
    val clients = ClientsRepository(sessions, appScope)
}

val Context.container: AppContainer
    get() = (applicationContext as AdCtrlApp).container
