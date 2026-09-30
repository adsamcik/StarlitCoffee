plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

// Check the exact published versions using the same credentials as dependency
// resolution. No cached/local artifacts can hide missing registry access.
val packageCredentialSource = extra["githubPackagesCredentialSource"] as String
@Suppress("UNCHECKED_CAST")
val packageRepositories = extra["githubPackageRepositories"] as List<List<String>>
val packageAuthChecks = listOf(
    Triple("MindlayerGitHubPackages", "com/adsamcik/mindlayer/sdk", libs.versions.mindlayer.get()),
    Triple("TraceboxGitHubPackages", "io/github/tracebox/tracebox", libs.versions.tracebox.get()),
).map { (repositoryName, artifactPath, version) ->
    val (_, repositoryUrl, username, token) = packageRepositories.single { it.first() == repositoryName }
    val artifactName = artifactPath.substringAfterLast('/')
    listOf(
        repositoryName.removeSuffix("GitHubPackages"),
        "$repositoryUrl/$artifactPath/$version/$artifactName-$version.pom",
        username,
        token,
    )
}

tasks.register("checkGitHubPackagesAuth") {
    group = "verification"
    description = "Verify access to the configured Mindlayer and Tracebox package versions."
    notCompatibleWithConfigurationCache("Authentication is checked live, using current credentials.")
    doLast {
        logger.lifecycle("GitHub Packages credential source: $packageCredentialSource")
        val failures = mutableListOf<String>()
        for ((name, endpoint, username, token) in packageAuthChecks) {
            if (token.isBlank()) {
                failures.add("$name: no credential. Run .\\scripts\\setup-github-packages.ps1; " +
                    "check that Gradle can find gh and access its credential store.")
                continue
            }
            val connection = java.net.URI(endpoint).toURL().openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                val basic = java.util.Base64.getEncoder().encodeToString("$username:$token".toByteArray(Charsets.UTF_8))
                connection.setRequestProperty("Authorization", "Basic $basic")
                val status = connection.responseCode
                if (status == 200) {
                    logger.lifecycle("$name: package access verified (HTTP 200)")
                } else {
                    val recovery = when (status) {
                        401 -> "Credential rejected. Use a classic token with read:packages. " +
                            "Replace/remove an explicit GH_TOKEN or GITHUB_TOKEN override if it is stale; " +
                            "for GitHub CLI run gh auth refresh --hostname github.com --scopes read:packages."
                        403 -> "Credential lacks package access. Check read:packages, repository access, " +
                            "and organization SSO; CI tokens must have access to both dependency repositories."
                        404 -> "Version is missing or inaccessible. Check the package version, GITHUB_OWNER, and account access."
                        else -> "Registry request failed. Check connectivity and GitHub Packages availability."
                    }
                    failures.add("$name: HTTP $status. $recovery")
                }
            } catch (_: java.io.IOException) {
                failures.add("$name: could not reach GitHub Packages. Check network/proxy settings and retry.")
            } finally {
                connection.disconnect()
            }
        }
        if (failures.isNotEmpty()) throw GradleException(failures.joinToString("\n"))
    }
}
