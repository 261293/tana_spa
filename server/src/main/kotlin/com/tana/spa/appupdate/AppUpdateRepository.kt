package com.tana.spa.appupdate

data class AppUpdateConfigRow(val minimumVersion: String, val latestVersion: String, val forceUpdate: Boolean)

/** Blocking; [AppUpdateService] moves the calls off the event loop. */
interface AppUpdateRepository {
    /** Null when nothing is stored, which a client reads as "no gate" rather than a blocked app. */
    fun config(): AppUpdateConfigRow?

    fun save(config: AppUpdateConfigRow, updatedBy: String?)
}

/** Used when `DATABASE_URL` is unset; anything set through the portal is lost on restart. */
class InMemoryAppUpdateRepository(initial: AppUpdateConfigRow? = null) : AppUpdateRepository {

    private var stored: AppUpdateConfigRow? = initial

    @Synchronized
    override fun config(): AppUpdateConfigRow? = stored

    @Synchronized
    override fun save(config: AppUpdateConfigRow, updatedBy: String?) {
        stored = config
    }
}
