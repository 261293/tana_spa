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
include(":core:mvi")
include(":core:navigation")
include(":core:network")
include(":core:ui")

include(":feature:Auth")
include(":feature:Explore")
include(":feature:Feed")
include(":feature:Likes")
include(":feature:NewPost")
include(":feature:Profile")

include(":server")
