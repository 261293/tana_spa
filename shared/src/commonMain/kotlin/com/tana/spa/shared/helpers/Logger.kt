package com.tana.spa.shared.helpers

interface Logger {
    fun log(tag: String, message: String)
}

class PrintLogger : Logger {
    override fun log(tag: String, message: String) {
        println("[$tag] $message")
    }
}
