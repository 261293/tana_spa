plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ktor)
}

group = "com.tana.spa"
version = "1.0.0"
application {
    mainClass = "com.tana.spa.ApplicationKt"
    // Run in UTC whatever the developer machine is set to, so a local run matches the
    // container and CI. It also keeps the JDBC driver from announcing a legacy zone id
    // (a JVM on Windows/Vietnam defaults to Asia/Saigon) that PostgreSQL may not know.
    applicationDefaultJvmArgs = listOf("-Duser.timezone=UTC")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationJson)
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverCallId)
    implementation(libs.ktor.serverForwardedHeader)
    implementation(libs.libphonenumber)
    implementation(libs.kotlinx.coroutines.core)
    // Database: pooled JDBC + Flyway migrations. Tests run against a real embedded PostgreSQL.
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql.driver)
    testImplementation(libs.embedded.postgres)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.kotlin.testJunit)
}

tasks.withType<Test>().configureEach {
    // Same reason as applicationDefaultJvmArgs: deterministic timestamps, and the embedded
    // PostgreSQL sees the same zone locally as it does on CI.
    jvmArgs("-Duser.timezone=UTC")
}
