package com.vknxworks.adctrl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.adctrl.data.api.WriteGuard
import com.vknxworks.adctrl.data.lock.LockVault
import com.vknxworks.adctrl.ui.lock.AppLock
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)

    /** The app lock's PIN, fingerprint key and the key that seals the saved passwords while locked. */
    val vault = LockVault(context, "adctrl")

    /** The app lock is locked: the passwords are sealed and requests wait. Locked from the start when the lock is on. */
    val locked = MutableStateFlow(vault.enabled)

    val serverStore = ServerStore(context, vault)

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

    val sessions = Sessions(serverStore, http, appScope, locked)
    val overview = OverviewRepository(context, sessions, appScope)
    val queries = QueriesRepository(sessions, appScope)
    val userRules = UserRules(sessions)
    val filters = FiltersRepository(sessions, appScope)
    val clients = ClientsRepository(sessions, appScope)

    /** Turns the app lock on with [pin]: every saved password is sealed with it from now on. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) {
            vault.setUp(pin)
            serverStore.applySealing(true)
        }
        locked.value = false
    }

    /** Turns the app lock off (after the PIN was confirmed): passwords go back to Keystore-only encryption. */
    suspend fun disableLock() {
        withContext(Dispatchers.Default) { serverStore.applySealing(false) }
        vault.disable()
        locked.value = false
        settings.update { it.copy(lockChanges = false) }
    }

    /** "Forgot PIN?": the sealed passwords can't be read any more, so remove every server and start over. */
    suspend fun forgetEverything() {
        withContext(Dispatchers.IO) {
            serverStore.servers.value.forEach { overview.forget(it.id) }
            serverStore.removeAll()
        }
        sessions.forgetSecrets()
        vault.disable()
        AppLock.markUnlocked()
        locked.value = false
        settings.update { it.copy(lockChanges = false) }
    }

    /** Locked: wipe the key and every decrypted password from memory; requests wait until unlocked. */
    fun onLocked() {
        vault.close()
        if (vault.enabled) {
            locked.value = true
            sessions.forgetSecrets()
        }
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
        locked.value = false
    }

    init {
        AppLock.onLocked = { onLocked() }
        // "Confirm changes": every request that could change something asks for the fingerprint or PIN first.
        WriteGuard.check = { AppLock.confirmChange(vault.enabled && settings.settings.value.lockChanges) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(vault.enabled, settings.settings.value.lockTimeout.millis)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onBackground(vault.enabled, settings.settings.value.lockTimeout.millis)
            }
        })
    }
}

val Context.container: AppContainer
    get() = (applicationContext as AdCtrlApp).container
