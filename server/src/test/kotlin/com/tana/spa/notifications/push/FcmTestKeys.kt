package com.tana.spa.notifications.push

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * A throw-away RSA key pair, generated per test run: a service-account key is a credential, so no
 * test here may carry one as a literal, not even a retired one.
 */
internal object FcmTestKeys {
    val keyPair: KeyPair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }

    /** PKCS#8 PEM, the format the Firebase console puts in `private_key`. */
    fun privateKeyPem(): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----\n"
    }

    /** As it appears inside a JSON key file or an environment variable: newlines escaped. */
    fun serviceAccountJson(
        projectId: String? = "tana-demo",
        clientEmail: String? = "push@tana-demo.iam.gserviceaccount.com",
        privateKey: String? = privateKeyPem(),
        tokenUri: String? = null,
    ): String = buildString {
        append("{")
        append(""""type": "service_account"""")
        projectId?.let { append(""", "project_id": "$it"""") }
        clientEmail?.let { append(""", "client_email": "$it"""") }
        privateKey?.let { append(""", "private_key": "${it.replace("\n", "\\n")}"""") }
        tokenUri?.let { append(""", "token_uri": "$it"""") }
        append("}")
    }
}
