pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChessAndCocoa"

// Layers, top to bottom; each depends only on the one(s) directly below:
//   :app (UI -> session) -> :coach -> :engine, :chess
// :harness is a desktop research tool on top of :coach, never shipped.
include(":chess", ":engine", ":coach", ":app", ":harness")
