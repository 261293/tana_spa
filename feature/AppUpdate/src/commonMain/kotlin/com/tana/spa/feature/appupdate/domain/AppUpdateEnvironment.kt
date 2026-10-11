package com.tana.spa.feature.appupdate.domain

/** Null when the platform will not say, and on the targets that have no store. */
fun interface InstalledAppVersion {
    fun current(): String?
}

fun interface StoreLauncher {
    fun open(url: String)
}
