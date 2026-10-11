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

    val envName: String = "FEATURE_" + key.uppercase()

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }

        fun fromEnvName(name: String): FeatureFlag? = entries.firstOrNull { it.envName.equals(name, ignoreCase = true) }
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

    companion object {
        private val TRUE_VALUES = setOf("true", "1", "yes", "on", "enabled")
        private val FALSE_VALUES = setOf("false", "0", "no", "off", "disabled")

        fun fromEnvironment(env: Map<String, String>): ConfigurableFeatureFlags =
            ConfigurableFeatureFlags(env.mapNotNull { overrideOrNull(it.key, it.value) }.toMap())

        // A name we do not know may belong to another tool sharing the environment, so it is
        // ignored; a value we cannot read was meant to change something, so it stops the server.
        private fun overrideOrNull(name: String, raw: String): Pair<FeatureFlag, Boolean>? {
            val flag = FeatureFlag.fromEnvName(name) ?: return null
            return flag to parseBoolean(flag, raw)
        }

        private fun parseBoolean(flag: FeatureFlag, raw: String): Boolean = when (raw.trim().lowercase()) {
            in TRUE_VALUES -> true
            in FALSE_VALUES -> false
            else -> throw IllegalArgumentException("${flag.envName}: '$raw' is not a boolean (true/false)")
        }
    }
}

fun FeatureFlags.describe(): String = FeatureFlag.entries.joinToString(", ") { flag ->
    flag.key + "=" + if (isEnabled(flag)) "on" else "off"
}
