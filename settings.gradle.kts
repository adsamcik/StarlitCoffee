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

// Explicit build/CI credentials override local configuration. Resolve once for
// both registries; never log tokens or GitHub CLI output.
val packageProps = java.util.Properties().apply {
    val localPropsFile = rootDir.resolve("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

fun packageCredential(key: String, fallback: String = ""): String {
    settings.providers.gradleProperty(key).orNull?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    settings.providers.environmentVariable(key).orNull?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    packageProps.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return fallback
}

class PackageToken(val value: String, val source: String)

fun resolveGitHubToken(): PackageToken {
    val keys = listOf("GH_TOKEN", "GITHUB_TOKEN")
    for ((source, lookup) in listOf<Pair<String, (String) -> String?>>(
        "Gradle property" to { settings.providers.gradleProperty(it).orNull },
        "environment" to { settings.providers.environmentVariable(it).orNull },
        "local.properties" to { packageProps.getProperty(it) },
    )) {
        for (key in keys) {
            lookup(key)?.trim()?.takeIf { it.isNotBlank() }?.let {
                return PackageToken(it, "$key ($source)")
            }
        }
    }

    // Android Studio may have started before gh was added to PATH. Also try
    // standard Windows install locations without shell commands or token files.
    val executables = listOfNotNull(
        "gh",
        settings.providers.environmentVariable("ProgramFiles").orNull?.let { "$it/GitHub CLI/gh.exe" },
        settings.providers.environmentVariable("LOCALAPPDATA").orNull?.let { "$it/Programs/GitHub CLI/gh.exe" },
    ).distinct()
    val account = packageCredential("GITHUB_USERNAME")
    for (executable in executables) {
        if (executable != "gh" && !java.io.File(executable).isFile) continue
        val command = mutableListOf(executable, "auth", "token", "--hostname", "github.com")
        if (account.isNotBlank()) command.addAll(listOf("--user", account))
        try {
            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            // Token output is small. Keep stderr separate and bound keyring access.
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                continue
            }
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (process.exitValue() == 0 && output.isNotBlank() && !output.contains('\n')) {
                return PackageToken(output, "GitHub CLI" + if (account.isNotBlank()) " ($account)" else " (active account)")
            }
        } catch (_: java.io.IOException) {
            // Try another installation; report the failure without CLI output.
        }
    }
    return PackageToken("", "unavailable")
}

val ghOwner = packageCredential("GITHUB_OWNER", "adsamcik").lowercase()
val ghUsername = packageCredential("GITHUB_USERNAME", packageCredential("GITHUB_ACTOR", ghOwner))
val ghToken = resolveGitHubToken()
gradle.rootProject {
    extra["githubPackagesCredentialSource"] = ghToken.source
    extra["githubPackageRepositories"] = settings.dependencyResolutionManagement.repositories
        .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
        .filter { it.name.endsWith("GitHubPackages") }
        .map { listOf(it.name, it.url.toString(), it.credentials.username.orEmpty(), it.credentials.password.orEmpty()) }
}
if (ghToken.value.isBlank() && !gradle.startParameter.isOffline) {
    logger.warn(
        "GitHub Packages credentials are unavailable. Run .\\scripts\\setup-github-packages.ps1 " +
            "or provide a classic token with read:packages via GITHUB_TOKEN. " +
            "If gh is already signed in, check that Gradle can access its credential store. " +
            "Run checkGitHubPackagesAuth to diagnose package access.",
    )
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            name = "MindlayerGitHubPackages"
            url = uri("https://maven.pkg.github.com/$ghOwner/Mindlayer")
            credentials {
                username = ghUsername
                password = ghToken.value
            }
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
            content {
                includeGroup("com.adsamcik.mindlayer")
            }
        }
        maven {
            name = "TraceboxGitHubPackages"
            url = uri("https://maven.pkg.github.com/$ghOwner/Tracebox")
            credentials {
                username = ghUsername
                password = ghToken.value
            }
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
            content {
                includeGroup("io.github.tracebox")
            }
        }
    }
}

rootProject.name = "StarlitCoffee"
include(":app")
