package com.tana.spa.notifications.push

import org.slf4j.LoggerFactory

/** What one device is asked to display, plus the payload the app reads when it is tapped. */
data class PushMessage(val title: String, val body: String?, val data: Map<String, String> = emptyMap())

sealed interface PushOutcome {
    val token: String

    data class Delivered(override val token: String) : PushOutcome

    /** The provider says this install is gone. The token is deleted, not retried. */
    data class Unregistered(override val token: String) : PushOutcome

    /** [retryable] separates an outage or a quota from a configuration mistake. */
    data class Failed(override val token: String, val reason: String, val retryable: Boolean) : PushOutcome
}

/**
 * Delivers a notification to device tokens. The rest of the server only knows this interface, so
 * the transport (FCM, APNs direct, a Vietnamese gateway) can be swapped without touching routes,
 * the database or the clients.
 *
 * Blocking, like the repositories; [com.tana.spa.notifications.NotificationService] moves the
 * calls to the IO dispatcher.
 */
interface PushSender {
    /** Recorded in logs, e.g. `fcm_http_v1`. */
    val name: String

    /** One outcome per token, in any order. Never throws: a dead provider is a per-token failure. */
    fun send(tokens: List<String>, message: PushMessage): List<PushOutcome>

    companion object {
        private val log = LoggerFactory.getLogger(PushSender::class.java)

        /**
         * FCM when a service account is configured, otherwise the offline dev sender.
         *
         *  - `FCM_SERVICE_ACCOUNT_JSON` the service-account key, inline
         *  - `FCM_SERVICE_ACCOUNT_FILE` or the same key as a file path (either one is enough)
         *  - `FCM_PROJECT_ID`           optional; defaults to the key's own `project_id`
         *
         * Unlike the OTP provider this does not refuse to boot in production: the notifications
         * flag ships off, so "configured for push" is not a precondition for a production server.
         * It logs loudly instead.
         */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): PushSender {
            val account = FcmConfig.fromEnvironment(env) ?: run {
                val appEnv = env["APP_ENV"]?.trim()?.lowercase() ?: "unset"
                log.warn(
                    "FCM is not configured (APP_ENV={}): using the DEV push sender. Notifications are " +
                        "stored and shown in the app, but nothing reaches a device.",
                    appEnv,
                )
                return DevPushSender()
            }
            log.info("Push delivery: FCM HTTP v1, project {}", account.projectId)
            return FcmPushSender(account)
        }
    }
}

/**
 * Writes what it would have sent to the server log. The notification itself is still stored, so
 * the in-app list works exactly as in production; only the device wake-up is missing.
 *
 * Logs a token prefix, never the token: a registration token is enough to push to that device.
 */
class DevPushSender : PushSender {

    override val name = "dev"

    override fun send(tokens: List<String>, message: PushMessage): List<PushOutcome> {
        tokens.forEach { token ->
            log.info(
                "DEV push to {} title='{}' dataKeys={}",
                tokenHint(token),
                message.title,
                message.data.keys,
            )
        }
        return tokens.map { PushOutcome.Delivered(it) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(DevPushSender::class.java)!!
    }
}

/** Enough to tell two devices apart in a log line, far too little to send to either. */
internal fun tokenHint(token: String): String = token.take(8) + "…(" + token.length + ")"
