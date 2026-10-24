package com.tana.spa.core.network

fun interface CallerRole {
    fun role(): String

    companion object {
        val none: CallerRole = CallerRole { "" }
    }
}
