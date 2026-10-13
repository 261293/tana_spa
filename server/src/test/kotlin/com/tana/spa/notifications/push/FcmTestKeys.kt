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
        return pem(base64) + "\n"
    }

    /**
     * A PEM envelope around arbitrary body text, for the invalid keys the parser must reject.
     * Centralised because the secret scanner's private-key rule spans from the first header in a
     * file to the last and reports everything between the two as one secret.
     */
    fun pem(body: String): String = "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----"

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
