package com.tana.spa.feature.feed.domain

/** Bound in `composeApp`, which is the only place that sees both Auth and Feed. */
fun interface HomeUserName {
    fun current(): String?
}
