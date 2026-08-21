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

// Resolve credentials for public GitHub Packages Maven repositories. GitHub
// still requires a token for Maven reads, so look up: local.properties ->
// -P project property -> environment variable -> `gh auth token`.
val packageProps = java.util.Properties().apply {
    val localPropsFile = rootDir.resolve("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

fun packageCredential(key: String, fallback: String = ""): String {
    packageProps.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
    settings.providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }?.let { return it }
    System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }
    return fallback
}

// Resolve a GitHub token. If no explicit token is configured, try `gh auth token`
// so developers with the GitHub CLI authenticated don't need to manage a PAT.
fun resolveGitHubToken(): String {
    val explicit = packageCredential("GITHUB_TOKEN")
    if (explicit.isNotBlank()) return explicit
    return try {
        val process = ProcessBuilder("gh", "auth", "token")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && output.isNotBlank()) output else ""
    } catch (_: Exception) {
        ""
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            name = "MindlayerGitHubPackages"
            val ghOwner = packageCredential("GITHUB_OWNER", "adsamcik")
            val ghToken = resolveGitHubToken()
            url = uri("https://maven.pkg.github.com/$ghOwner/Mindlayer")
            credentials {
                username = ghOwner
                password = ghToken
            }
            content {
                includeGroup("com.adsamcik.mindlayer")
            }
        }
        maven {
            name = "TraceboxGitHubPackages"
            val ghOwner = packageCredential("GITHUB_OWNER", "adsamcik")
            val ghToken = resolveGitHubToken()
            url = uri("https://maven.pkg.github.com/$ghOwner/Tracebox")
            credentials {
                username = ghOwner
                password = ghToken
            }
            content {
                includeGroup("io.github.tracebox")
            }
        }
    }
}

rootProject.name = "StarlitCoffee"
include(":app")
