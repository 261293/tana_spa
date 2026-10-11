rootProject.name = "Tana_spa"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":androidApp")
include(":shared")

include(":core:cache")
include(":core:featureflags")
include(":core:logging")
include(":core:mvi")
include(":core:navigation")
include(":core:network")
include(":core:notifications")
include(":core:ui")

include(":feature:AppUpdate")
include(":feature:Assistant")
include(":feature:Auth")
include(":feature:Explore")
include(":feature:Feed")
include(":feature:Likes")
include(":feature:NewPost")
include(":feature:Notifications")
include(":feature:Profile")

include(":server")
