package com.tana.spa.shared.featureflags

enum class FeatureFlag(val key: String, val defaultEnabled: Boolean, val description: String) {
    PHONE_OTP_VERIFICATION(
        key = "phone_otp_verification",
        // Off because the flow is unreliable, not because it is unfinished. See docs/feature-flags.md.
        defaultEnabled = false,
        description = "Phone-number verification with SMS one-time codes",
    ),
    APP_UPDATE_GATE(
        key = "app_update_gate",
        // A kill switch, not a temporary flag: it is the only remote way to stop a bad
        // minimumVersion locking every installed app out. On because an absent config row
        // already means "no gate".
        defaultEnabled = true,
        description = "Force and optional app-update prompts driven by app_update_config",
    ),
    NOTIFICATIONS(
        key = "notifications",
        // Off until a Firebase project is configured: with no credentials the server stores
        // tokens and delivers nothing, and the bell would show a list that never fills.
        defaultEnabled = false,
        description = "Notification centre and push delivery to registered devices",
    ),
    ;

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

fun interface FeatureFlags {
    fun isEnabled(flag: FeatureFlag): Boolean

    companion object {
        val defaults: FeatureFlags = FeatureFlags { it.defaultEnabled }
    }
}

class ConfigurableFeatureFlags(overrides: Map<FeatureFlag, Boolean> = emptyMap()) : FeatureFlags {

    private val overrides: Map<FeatureFlag, Boolean> = overrides.toMap()

    override fun isEnabled(flag: FeatureFlag): Boolean = overrides[flag] ?: flag.defaultEnabled
}

fun FeatureFlags.describe(): String = FeatureFlag.entries.joinToString(", ") { flag ->
    flag.key + "=" + if (isEnabled(flag)) "on" else "off"
}
