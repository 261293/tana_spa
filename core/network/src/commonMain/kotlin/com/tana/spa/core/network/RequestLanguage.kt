package com.tana.spa.core.network

/**
 * The language tag sent as `Accept-Language` on every call, so the server answers with the
 * catalogue in the language the app is showing — treatment names are data, not resources, and the
 * device's own locale is not what the guest chose in Settings.
 *
 * A plain holder rather than an injected dependency: the app root already watches the chosen
 * language to re-read its strings and writes it here in the same place, and `core:network` has no
 * business depending on the theme to learn one tag.
 */
object RequestLanguage {
    /** A BCP 47 tag; Vietnamese is what the app opens in before Settings is read. */
    var tag: String = "vi"
}
