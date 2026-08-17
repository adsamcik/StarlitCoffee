[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$githubHost = "github.com"
$requiredScope = "read:packages"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw @"
GitHub CLI is required but was not found.
Install it from https://cli.github.com/ and run this script again.
"@
}

Write-Host "Checking GitHub Packages authentication..."
$authStatus = (& gh auth status --hostname $githubHost 2>&1 | Out-String).Trim()
$isAuthenticated = $LASTEXITCODE -eq 0
$hasPackageScope = $isAuthenticated -and $authStatus -match "(^|[^A-Za-z0-9_:])read:packages([^A-Za-z0-9_:]|$)"

if (-not $isAuthenticated) {
    Write-Host "Sign in to GitHub and approve read access to packages."
    & gh auth login `
        --hostname $githubHost `
        --git-protocol https `
        --web `
        --scopes $requiredScope

    if ($LASTEXITCODE -ne 0) {
        throw "GitHub sign-in did not complete successfully."
    }
} elseif (-not $hasPackageScope) {
    Write-Host "The current GitHub login needs permission to read packages."
    & gh auth refresh `
        --hostname $githubHost `
        --scopes $requiredScope

    if ($LASTEXITCODE -ne 0) {
        throw "GitHub package permission was not granted."
    }
} else {
    Write-Host "GitHub Packages authentication is already configured."
}

# Gradle obtains this token by invoking `gh auth token`. Keep the token in the
# GitHub CLI credential store and never print or copy it into a project file.
$token = (& gh auth token --hostname $githubHost 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($token)) {
    throw "GitHub CLI did not return a token after authentication."
}

$token = $null
Write-Host "Ready. Gradle can now download the Mindlayer SDK from GitHub Packages."
