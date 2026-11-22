package com.vknxworks.adctrl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.adctrl.data.api.WriteGuard
import com.vknxworks.adctrl.data.lock.LockVault
import com.vknxworks.adctrl.data.notify.CheckStore
import com.vknxworks.adctrl.data.notify.Notifier
import com.vknxworks.adctrl.data.notify.NotifySettingsRepository
import com.vknxworks.adctrl.data.notify.NotifyWorker
import com.vknxworks.adctrl.data.notify.Push
import com.vknxworks.adctrl.data.notify.PushStatus
import com.vknxworks.adctrl.ui.lock.AppLock
import com.vknxworks.adctrl.data.clients.ClientsRepository
import com.vknxworks.adctrl.data.dns.DnsRepository
import com.vknxworks.adctrl.data.filters.FiltersRepository
import com.vknxworks.adctrl.data.overview.OverviewRepository
import com.vknxworks.adctrl.data.queries.QueriesRepository
import com.vknxworks.adctrl.data.rules.UserRules
import com.vknxworks.adctrl.data.server.HostAllowlist
import com.vknxworks.adctrl.data.server.ServerStore
import com.vknxworks.adctrl.data.server.Sessions
import com.vknxworks.adctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.WorkManager
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** A launcher shortcut, waiting for the main screen (and the app lock) to handle it. */
enum class ShortcutAction { PAUSE, QUERIES, SEARCH, BLOCKED }

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /**
     * Fire-and-forget work that outlives a screen (saving a setting, a refresh after a change, a push
     * from ntfy). A failure there (storage, network, a server answering oddly) must never crash AdCtrl,
     * least of all while it's being closed, so it ends that job only. Nothing is logged.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)

    /** Background checks and what they notify about. */
    val notifySettings = NotifySettingsRepository(context, appScope)

    /** The app lock's PIN, fingerprint key and the key that seals the saved passwords while locked. */
    val vault = LockVault(context, "adctrl")

    /** The app lock is locked: the passwords are sealed and requests wait. Locked from the start when the lock is on. */
    val locked = MutableStateFlow(vault.enabled)

    val serverStore = ServerStore(context, vault).apply {
        // Background checks sign in while AdCtrl is locked, so with them on the passwords stay
        // encrypted with the Keystore only (as without the app lock).
        sealPolicy = { sealing() }
    }

    /** Passwords are sealed with the app lock: the lock is on and background checks are off. */
    fun sealing(): Boolean = vault.enabled && !notifySettings.pollingNow()

    /** Set by MainActivity when a launcher shortcut opened AdCtrl; the main screen handles it after unlocking. */
    val pendingShortcut = MutableStateFlow<ShortcutAction?>(null)

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
    val dns = DnsRepository(sessions)

    /** Turns the app lock on with [pin]: every saved password is sealed with it from now on. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) {
            vault.setUp(pin)
            serverStore.applySealing(sealing())
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
            com.vknxworks.adctrl.data.timed.PauseSecrets.clearAll(context)
            CheckStore.forgetAll(context)
        }
        Notifier.cancelAll(context)
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
        // Seal any password left unsealed (AdCtrl stopped between setting up the lock and sealing them),
        // and match background checks turned on or off since.
        if (vault.enabled) appScope.launch { runCatching { serverStore.applySealing(sealing()) } }
    }

    /**
     * The home screen widget and the Quick Settings tile were removed. An install that had the widget placed
     * may still have its refresh job waiting and its saved numbers: cancel the job and delete the file. Runs
     * off the main thread and is harmless when there is nothing to clean up.
     */
    fun cleanUpRemovedWidget() {
        appScope.launch(Dispatchers.IO) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork("adctrl-widget") }
            runCatching { File(context.noBackupFilesDir, "widget.json").delete() }
        }
    }

    /** A server was removed: its notifications and check state go too. */
    fun forgetServer(id: String) {
        overview.forget(id)
        CheckStore.forget(context, id)
        Notifier.cancelServer(context, id)
        // Its timed jobs and the password copy kept for the pause-end job go too.
        appScope.launch(Dispatchers.IO) {
            com.vknxworks.adctrl.data.timed.PauseSecrets.clear(context, id)
            runCatching { WorkManager.getInstance(context).cancelUniqueWork("adctrl-resume-$id") }
        }
    }

    init {
        AppLock.onLocked = { onLocked() }
        // Background checks follow their settings; turning them on or off unseals or seals the
        // passwords again (only possible while unlocked, where the settings are).
        appScope.launch {
            val first = notifySettings.current()
            NotifyWorker.schedule(context, first)
            // Push through ntfy: register again at every start (same address), register when turned on, drop
            // it when turned off. It follows its own switch only, so pausing background checks keeps the address.
            Push.start(context, first.push)
            launch {
                notifySettings.settings.drop(1).map { it.push }.distinctUntilChanged().collect { on ->
                    when {
                        on -> Push.register(context)
                        Push.status.value !is PushStatus.Off -> Push.unregister(context)
                    }
                }
            }
            notifySettings.settings.drop(1).map { it.polling to it.intervalMinutes }.distinctUntilChanged().collect { (polling, _) ->
                NotifyWorker.schedule(context, notifySettings.current())
                if (vault.isOpen) withContext(Dispatchers.Default) { serverStore.applySealing(vault.enabled && !polling) }
            }
        }
        // "Confirm changes": every request that could change something asks for the fingerprint or PIN first.
        // Read from storage, not the in-memory copy: right after a cold start that copy may still hold the
        // defaults (Confirm changes off) for a moment.
        WriteGuard.check = { AppLock.confirmChange(vault.enabled && settings.current().lockChanges) }
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
