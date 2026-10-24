package com.tana.spa.booking

import com.tana.spa.SpaServices
import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.Pbkdf2PasswordHasher
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.db.TestDatabase
import com.tana.spa.module
import com.tana.spa.notifications.PostgresNotificationRepository
import com.tana.spa.promotions.PromotionService
import com.tana.spa.shared.booking.BookingApi
import com.tana.spa.shared.booking.CatalogResponse
import com.tana.spa.shared.booking.ServiceDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The menu is data, not resources, so the language the app is drawing itself in travels with the
 * request. `V9` seeds the treatments in English and `V11` writes the Vietnamese beside them.
 */
class CatalogLanguageTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val dataSource = TestDatabase.dataSource

    private val auth: AuthServices by lazy {
        AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = PostgresUserRepository(dataSource),
            hasher = Pbkdf2PasswordHasher(iterations = 1_000),
            notificationRepository = PostgresNotificationRepository(dataSource),
        )
    }

    private fun spa(): SpaServices {
        val repository = PostgresBookingRepository(dataSource)
        val booking = BookingService(
            repository = repository,
            notifications = auth.notifications,
            zone = zone,
            clock = { Instant.now() },
        )
        return SpaServices(booking, PromotionService(repository, PostgresUserRepository(dataSource), auth.notifications))
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.catalogIn(language: String): CatalogResponse =
        get(BookingApi.CATALOG) { header(HttpHeaders.AcceptLanguage, language) }.body()

    private fun CatalogResponse.service(slug: String): ServiceDto =
        assertNotNull(services.firstOrNull { it.slug == slug }, "$slug is missing from the menu")

    @Test
    fun a_vietnamese_app_reads_the_menu_in_vietnamese() = testApplication {
        application { module(auth, spa()) }
        val stone = jsonClient().catalogIn("vi").service("warm-stone-massage")

        assertEquals("Massage đá nóng", stone.name)
        assertEquals("Đá bazan, day ấn toàn thân, massage da đầu", stone.includes)
    }

    @Test
    fun an_english_app_reads_the_menu_as_the_spa_wrote_it() = testApplication {
        application { module(auth, spa()) }
        val stone = jsonClient().catalogIn("en-US,en;q=0.9").service("warm-stone-massage")

        assertEquals("Warm Stone Massage", stone.name)
        assertEquals("Basalt stones, full-body pressure work, scalp finish", stone.includes)
    }

    @Test
    fun the_therapist_is_described_in_the_language_asked_for() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()

        val vietnamese = client.catalogIn("vi").therapists.first { it.slug == "mai" }
        val english = client.catalogIn("en").therapists.first { it.slug == "mai" }

        assertEquals("Đá nóng & massage sâu", vietnamese.focus)
        assertEquals("Stone & deep tissue", english.focus)
        // A person's name is their name in either language.
        assertEquals(english.name, vietnamese.name)
    }

    @Test
    fun a_treatment_written_in_one_language_only_is_still_shown_in_the_other() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()

        // Nothing in the menu was ever written twice for this one: V9 seeds it, V11 skips it.
        val untranslated = client.catalogIn("en").services.map { it.slug } - setOf(
            "warm-stone-massage",
            "jade-roller-facial",
            "deep-tissue-massage",
            "matcha-body-polish",
            "herbal-compress-ritual",
        ).toSet()

        val vietnamese = client.catalogIn("vi")
        val english = client.catalogIn("en")
        untranslated.forEach { slug ->
            assertEquals(english.service(slug).name, vietnamese.service(slug).name, slug)
        }
    }

    @Test
    fun an_app_that_says_nothing_about_language_reads_the_menu_as_the_spa_wrote_it() = testApplication {
        application { module(auth, spa()) }
        val menu = jsonClient().get(BookingApi.CATALOG).body<CatalogResponse>()

        assertTrue(menu.services.any { it.name == "Warm Stone Massage" }, "the English name is the fallback")
    }
}
