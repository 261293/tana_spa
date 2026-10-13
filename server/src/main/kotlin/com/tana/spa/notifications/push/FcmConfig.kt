package com.tana.spa.notifications.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * The parts of a Google service-account key that FCM needs. Built from the JSON the Firebase
 * console downloads ("Project settings → Service accounts → Generate new private key").
 *
 * The key is a credential: nothing here is logged, and [toString] is not overridden to include it.
 */
class FcmConfig(
    val projectId: String,
    val clientEmail: String,
    val privateKey: PrivateKey,
    val tokenUri: String = DEFAULT_TOKEN_URI,
    /** Overridable so tests can point the sender at a local fake. */
    val sendBaseUrl: String = DEFAULT_SEND_BASE_URL,
) {
    val sendUrl: String = "$sendBaseUrl/v1/projects/$projectId/messages:send"

    companion object {
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
        const val DEFAULT_SEND_BASE_URL = "https://fcm.googleapis.com"

        /** Null when no service account is configured, which selects [DevPushSender]. */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): FcmConfig? {
            val inline = env["FCM_SERVICE_ACCOUNT_JSON"]?.takeIf { it.isNotBlank() }
            val path = env["FCM_SERVICE_ACCOUNT_FILE"]?.takeIf { it.isNotBlank() }
            val raw = when {
                inline != null -> inline

                path != null -> File(path).let {
                    require(it.isFile) { "FCM_SERVICE_ACCOUNT_FILE: no file at ${it.absolutePath}" }
                    it.readText()
                }

                else -> return null
            }
            return parse(raw, projectIdOverride = env["FCM_PROJECT_ID"]?.takeIf { it.isNotBlank() })
        }

        fun parse(serviceAccountJson: String, projectIdOverride: String? = null): FcmConfig {
            val key = try {
                json.decodeFromString<ServiceAccountKey>(serviceAccountJson)
            } catch (e: Exception) {
                // The message would quote the key material, so only the type is surfaced.
                throw IllegalArgumentException("FCM service account is not readable JSON (${e::class.simpleName})")
            }
            val projectId = projectIdOverride ?: key.projectId
            require(!projectId.isNullOrBlank()) { "FCM service account has no project_id; set FCM_PROJECT_ID" }
            require(!key.clientEmail.isNullOrBlank()) { "FCM service account has no client_email" }
            require(!key.privateKey.isNullOrBlank()) { "FCM service account has no private_key" }
            return FcmConfig(
                projectId = projectId,
                clientEmail = key.clientEmail,
                privateKey = decodePrivateKey(key.privateKey),
                tokenUri = key.tokenUri?.takeIf { it.isNotBlank() } ?: DEFAULT_TOKEN_URI,
            )
        }

        /**
         * PKCS#8 PEM. An environment variable usually carries the newlines escaped, so the literal
         * two characters `\n` are turned back into line breaks before the Base64 is read.
         */
        private fun decodePrivateKey(pem: String): PrivateKey {
            val base64 = pem
                .replace("\\n", "\n")
                .lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString("") { it.trim() }
            require(base64.isNotEmpty()) { "FCM service account private_key is empty" }
            val decoded = try {
                Base64.getDecoder().decode(base64)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("FCM service account private_key is not valid Base64", e)
            }
            return try {
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decoded))
            } catch (e: Exception) {
                throw IllegalArgumentException("FCM service account private_key is not a PKCS#8 RSA key (${e::class.simpleName})")
            }
        }

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class ServiceAccountKey(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("client_email") val clientEmail: String? = null,
    @SerialName("private_key") val privateKey: String? = null,
    @SerialName("token_uri") val tokenUri: String? = null,
)
