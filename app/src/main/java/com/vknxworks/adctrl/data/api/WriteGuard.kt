package com.vknxworks.adctrl.data.api

/**
 * Asked before every request that could change something on a server (anything but GET), after the
 * read-only check. The app lock will set [check] to ask for the fingerprint or screen lock; it returns
 * false when the user cancels, and the request fails with [AghError.Cancelled] without being sent.
 * By default everything is allowed.
 */
object WriteGuard {
    @Volatile
    var check: suspend () -> Boolean = { true }
}
