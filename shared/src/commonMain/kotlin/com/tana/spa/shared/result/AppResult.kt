package com.tana.spa.shared.result

import kotlin.coroutines.cancellation.CancellationException

/** Failure carried across layer boundaries instead of throwing. */
data class AppError(val message: String? = null, val cause: Throwable? = null)

/** Result of an operation that can fail in an expected way (network, IO). */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/**
 * Runs [block] and wraps the outcome. Unlike `runCatching`, coroutine cancellation is
 * rethrown so structured concurrency keeps working.
 */
suspend inline fun <T> appResultOf(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(AppError(e.message, e))
}
