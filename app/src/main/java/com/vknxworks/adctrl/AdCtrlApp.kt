package com.vknxworks.adctrl

import android.app.Application
import com.vknxworks.adctrl.data.notify.Notifier

class AdCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
    }
}
