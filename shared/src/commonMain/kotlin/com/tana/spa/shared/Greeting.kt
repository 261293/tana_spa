package com.tana.spa.shared

fun sayHello(to: String): String = "Hello, $to!"

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = sayHello(platform.name)
}
