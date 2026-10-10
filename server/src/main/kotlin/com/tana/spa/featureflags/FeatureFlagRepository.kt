package com.tana.spa.featureflags

data class FeatureFlagRow(val key: String, val enabled: Boolean, val description: String? = null)

data class FeatureFlagOverrideRow(val key: String, val userId: String, val enabled: Boolean, val note: String? = null)

/** Blocking; [FeatureFlagService] moves the calls off the event loop. An absent key is not `false`. */
interface FeatureFlagRepository {
    fun globals(): Map<String, Boolean>

    fun overridesFor(userId: String): Map<String, Boolean>

    fun allOverrides(): List<FeatureFlagOverrideRow>

    fun setGlobal(key: String, enabled: Boolean, description: String?, updatedBy: String?)

    /** @return false when there was nothing stored to clear. */
    fun clearGlobal(key: String): Boolean

    fun setOverride(key: String, userId: String, enabled: Boolean, note: String?, updatedBy: String?)

    /** @return false when the user had no override. */
    fun clearOverride(key: String, userId: String): Boolean
}

/** Used when `DATABASE_URL` is unset; anything set through the portal is lost on restart. */
class InMemoryFeatureFlagRepository(initialGlobals: Map<String, Boolean> = emptyMap()) : FeatureFlagRepository {

    private val globals = LinkedHashMap<String, Boolean>(initialGlobals)
    private val overrides = LinkedHashMap<Pair<String, String>, FeatureFlagOverrideRow>()

    @Synchronized
    override fun globals(): Map<String, Boolean> = LinkedHashMap(globals)

    @Synchronized
    override fun overridesFor(userId: String): Map<String, Boolean> = overrides.values
        .filter { it.userId == userId }
        .associate { it.key to it.enabled }

    @Synchronized
    override fun allOverrides(): List<FeatureFlagOverrideRow> = overrides.values.toList()

    @Synchronized
    override fun setGlobal(key: String, enabled: Boolean, description: String?, updatedBy: String?) {
        globals[key] = enabled
    }

    @Synchronized
    override fun clearGlobal(key: String): Boolean = globals.remove(key) != null

    @Synchronized
    override fun setOverride(key: String, userId: String, enabled: Boolean, note: String?, updatedBy: String?) {
        overrides[key to userId] = FeatureFlagOverrideRow(key, userId, enabled, note)
    }

    @Synchronized
    override fun clearOverride(key: String, userId: String): Boolean = overrides.remove(key to userId) != null
}
