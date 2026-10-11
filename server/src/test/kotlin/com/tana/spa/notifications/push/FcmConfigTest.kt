package com.tana.spa.notifications.push

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FcmConfigTest {

    @Test
    fun anEnvironmentWithoutAServiceAccountSelectsTheDevSender() {
        assertNull(FcmConfig.fromEnvironment(emptyMap()))
        assertNull(FcmConfig.fromEnvironment(mapOf("FCM_SERVICE_ACCOUNT_JSON" to "  ")))
        assertIs<DevPushSender>(PushSender.fromEnvironment(emptyMap()))
    }

    @Test
    fun anInlineKeyIsReadWithItsNewlinesEscaped() {
        val config = FcmConfig.fromEnvironment(mapOf("FCM_SERVICE_ACCOUNT_JSON" to FcmTestKeys.serviceAccountJson()))

        assertTrue(config != null)
        assertEquals("tana-demo", config.projectId)
        assertEquals("push@tana-demo.iam.gserviceaccount.com", config.clientEmail)
        assertEquals("RSA", config.privateKey.algorithm)
        assertEquals(FcmConfig.DEFAULT_TOKEN_URI, config.tokenUri)
        assertEquals("https://fcm.googleapis.com/v1/projects/tana-demo/messages:send", config.sendUrl)
    }

    @Test
    fun aKeyFileIsReadFromItsPath() {
        val file = File.createTempFile("fcm-service-account", ".json").apply {
            deleteOnExit()
            writeText(FcmTestKeys.serviceAccountJson())
        }

        val config = FcmConfig.fromEnvironment(mapOf("FCM_SERVICE_ACCOUNT_FILE" to file.absolutePath))

        assertEquals("tana-demo", config?.projectId)
        assertIs<FcmPushSender>(PushSender.fromEnvironment(mapOf("FCM_SERVICE_ACCOUNT_FILE" to file.absolutePath)))
    }

    @Test
    fun aMissingKeyFileStopsTheServerRatherThanDeliveringNothingQuietly() {
        val error = assertFailsWith<IllegalArgumentException> {
            FcmConfig.fromEnvironment(mapOf("FCM_SERVICE_ACCOUNT_FILE" to "no/such/key.json"))
        }

        assertTrue(error.message!!.contains("FCM_SERVICE_ACCOUNT_FILE"), error.message!!)
    }

    @Test
    fun theProjectCanBeOverriddenForAKeySharedBetweenProjects() {
        val config = FcmConfig.fromEnvironment(
            mapOf(
                "FCM_SERVICE_ACCOUNT_JSON" to FcmTestKeys.serviceAccountJson(),
                "FCM_PROJECT_ID" to "tana-staging",
            ),
        )

        assertEquals("tana-staging", config?.projectId)
    }

    @Test
    fun aTokenUriInTheKeyIsHonoured() {
        val config = FcmConfig.parse(FcmTestKeys.serviceAccountJson(tokenUri = "https://oauth2.example.com/token"))

        assertEquals("https://oauth2.example.com/token", config.tokenUri)
    }

    @Test
    fun anIncompleteKeyIsRejectedWithAFieldNameAndNoKeyMaterial() {
        val noProject = assertFailsWith<IllegalArgumentException> {
            FcmConfig.parse(FcmTestKeys.serviceAccountJson(projectId = null))
        }
        assertTrue(noProject.message!!.contains("FCM_PROJECT_ID"), noProject.message!!)

        assertFailsWith<IllegalArgumentException> { FcmConfig.parse(FcmTestKeys.serviceAccountJson(clientEmail = null)) }
        assertFailsWith<IllegalArgumentException> { FcmConfig.parse(FcmTestKeys.serviceAccountJson(privateKey = null)) }
    }

    @Test
    fun aKeyThatIsNotAPkcs8RsaKeyIsRejectedWithoutQuotingIt() {
        val notBase64 = assertFailsWith<IllegalArgumentException> {
            FcmConfig.parse(
                FcmTestKeys.serviceAccountJson(privateKey = "-----BEGIN PRIVATE KEY-----\nnot base64!\n-----END PRIVATE KEY-----"),
            )
        }
        assertTrue(notBase64.message!!.contains("Base64"), notBase64.message!!)
        assertTrue(!notBase64.message!!.contains("not base64!"), "the message must not echo key material")

        val notAKey = assertFailsWith<IllegalArgumentException> {
            FcmConfig.parse(FcmTestKeys.serviceAccountJson(privateKey = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"))
        }
        assertTrue(notAKey.message!!.contains("PKCS#8"), notAKey.message!!)
    }

    @Test
    fun brokenJsonIsReportedByTypeOnlyBecauseTheParserMessageWouldQuoteTheKey() {
        val error = assertFailsWith<IllegalArgumentException> { FcmConfig.parse("""{"project_id": """) }

        assertTrue(error.message!!.contains("not readable JSON"), error.message!!)
    }
}
