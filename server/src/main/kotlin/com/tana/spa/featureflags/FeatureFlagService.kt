package com.tana.spa.featureflags

import com.tana.spa.auth.UserRepository
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlagAdminDto
import com.tana.spa.shared.featureflags.FeatureFlagOverrideDto
import com.tana.spa.shared.featureflags.FeatureFlags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

sealed interface FlagWriteResult {
    data object Applied : FlagWriteResult
    data object NotStored : FlagWriteResult
    data object UnknownUser : FlagWriteResult
}

/**
 * Resolves a flag as **override → global row → [deployDefaults] → compiled default**, so a
 * missing row means "no opinion" and clearing one is an undo rather than an off switch.
 */
class FeatureFlagService(
    private val repository: FeatureFlagRepository,
    private val users: UserRepository,
    private val deployDefaults: FeatureFlags = FeatureFlags.defaults,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Reads the database every time: a flag is flipped to stop something going wrong, so it has to
     * take effect on the next request rather than when a cache expires.
     */
    suspend fun isEnabled(flag: FeatureFlag, userId: String? = null): Boolean = withContext(io) {
        resolve(flag, overridesFor(userId), repository.globals())
    }

    suspend fun resolveAll(userId: String? = null): Map<String, Boolean> = withContext(io) {
        val overrides = overridesFor(userId)
        val globals = repository.globals()
        FeatureFlag.entries.associate { it.key to resolve(it, overrides, globals) }
    }

    suspend fun adminView(): List<FeatureFlagAdminDto> = withContext(io) {
        val globals = repository.globals()
        val overrides = repository.allOverrides().groupBy { it.key }
        FeatureFlag.entries.map { flag -> flag.toAdminDto(globals[flag.key], overrides[flag.key].orEmpty()) }
    }

    suspend fun setGlobal(flag: FeatureFlag, enabled: Boolean, actor: String): FlagWriteResult = withContext(io) {
        repository.setGlobal(flag.key, enabled, flag.description, actor)
        log.info("feature flag {} set to {} for everybody by {}", flag.key, enabled, actor)
        FlagWriteResult.Applied
    }

    suspend fun clearGlobal(flag: FeatureFlag, actor: String): FlagWriteResult = withContext(io) {
        if (!repository.clearGlobal(flag.key)) {
            FlagWriteResult.NotStored
        } else {
            log.info("feature flag {} cleared by {}, back to the deploy default", flag.key, actor)
            FlagWriteResult.Applied
        }
    }

    suspend fun setForUser(flag: FeatureFlag, userId: String, enabled: Boolean, note: String?, actor: String): FlagWriteResult =
        withContext(io) {
            // Checked here rather than left to the foreign key, so both stores answer alike.
            if (users.findById(userId) == null) return@withContext FlagWriteResult.UnknownUser
            repository.setOverride(flag.key, userId, enabled, note, actor)
            log.info("feature flag {} set to {} for user {} by {}", flag.key, enabled, userId, actor)
            FlagWriteResult.Applied
        }

    suspend fun clearForUser(flag: FeatureFlag, userId: String, actor: String): FlagWriteResult = withContext(io) {
        if (!repository.clearOverride(flag.key, userId)) {
            FlagWriteResult.NotStored
        } else {
            log.info("feature flag {} override for user {} removed by {}", flag.key, userId, actor)
            FlagWriteResult.Applied
        }
    }

    private fun resolve(flag: FeatureFlag, overrides: Map<String, Boolean>, globals: Map<String, Boolean>): Boolean =
        overrides[flag.key] ?: globals[flag.key] ?: deployDefaults.isEnabled(flag)

    private fun overridesFor(userId: String?): Map<String, Boolean> = userId?.let(repository::overridesFor).orEmpty()

    private fun FeatureFlag.toAdminDto(globalEnabled: Boolean?, overrides: List<FeatureFlagOverrideRow>): FeatureFlagAdminDto {
        val deployDefault = deployDefaults.isEnabled(this)
        return FeatureFlagAdminDto(
            key = key,
            description = description,
            enabled = globalEnabled ?: deployDefault,
            globalEnabled = globalEnabled,
            deployDefault = deployDefault,
            overrides = overrides.map { FeatureFlagOverrideDto(it.userId, it.enabled, it.note) },
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(FeatureFlagService::class.java)!!
    }
}
