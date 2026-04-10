pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "MindlayerGitHubPackages"
            val ghOwner = extra.properties["GITHUB_OWNER"]?.toString() ?: System.getenv("GITHUB_OWNER") ?: "OWNER"
            val ghToken = extra.properties["GITHUB_TOKEN"]?.toString() ?: System.getenv("GITHUB_TOKEN") ?: ""
            url = uri("https://maven.pkg.github.com/$ghOwner/Mindlayer")
            credentials {
                username = ghOwner
                password = ghToken
            }
            content {
                includeGroup("com.mindlayer")
            }
        }
    }
}

rootProject.name = "StarlitCoffee"
include(":app")
