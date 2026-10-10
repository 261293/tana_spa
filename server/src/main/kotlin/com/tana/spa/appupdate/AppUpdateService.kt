package com.tana.spa.appupdate

import com.tana.spa.shared.appupdate.AppUpdateConfigResponse
import com.tana.spa.shared.appupdate.AppUpdateConfigWriteRequest
import com.tana.spa.shared.appupdate.AppVersion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

sealed interface AppUpdateWriteResult {
    data object Applied : AppUpdateWriteResult
    data class InvalidVersion(val field: String, val value: String) : AppUpdateWriteResult
    data object MinimumAboveLatest : AppUpdateWriteResult
}

class AppUpdateService(private val repository: AppUpdateRepository, private val io: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun config(): AppUpdateConfigResponse? = withContext(io) {
        repository.config()?.let { AppUpdateConfigConverter.toResponse(it) }
    }

    suspend fun save(request: AppUpdateConfigWriteRequest, actor: String): AppUpdateWriteResult = withContext(io) {
        val minimum = AppVersion.parseOrNull(request.minimumVersion)
            ?: return@withContext AppUpdateWriteResult.InvalidVersion("minimumVersion", request.minimumVersion)
        val latest = AppVersion.parseOrNull(request.latestVersion)
            ?: return@withContext AppUpdateWriteResult.InvalidVersion("latestVersion", request.latestVersion)
        // A floor above the newest release would push every client towards a version that does not
        // exist in any store, which is the one state a user cannot escape.
        if (minimum > latest) return@withContext AppUpdateWriteResult.MinimumAboveLatest

        // Stored normalised, because the column's check constraint requires three segments.
        repository.save(AppUpdateConfigRow(minimum.toString(), latest.toString(), request.forceUpdate), actor)
        log.info("app update gate set to minimum={} latest={} force={} by {}", minimum, latest, request.forceUpdate, actor)
        AppUpdateWriteResult.Applied
    }

    private companion object {
        val log = LoggerFactory.getLogger(AppUpdateService::class.java)!!
    }
}

private object AppUpdateConfigConverter {
    fun toResponse(row: AppUpdateConfigRow) = AppUpdateConfigResponse(
        minimumVersion = row.minimumVersion,
        latestVersion = row.latestVersion,
        forceUpdate = row.forceUpdate,
    )
}
