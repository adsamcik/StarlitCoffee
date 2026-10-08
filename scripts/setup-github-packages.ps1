[CmdletBinding()]
param([switch]$CheckOnly)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$githubHost = "github.com"
$requiredScope = "read:packages"
$script:credentialSource = ""

function Test-PackageAccess {
    # Verify the credentials Gradle actually selects, including CI/property overrides.
    # Windows PowerShell wraps native stderr as ErrorRecords; inspect the exit
    # code instead of aborting on Gradle's expected authentication failure.
    $ErrorActionPreference = "Continue"
    $wrapper = if ($env:OS -eq "Windows_NT") { "gradlew.bat" } else { "gradlew" }
    & (Join-Path $repositoryRoot $wrapper) checkGitHubPackagesAuth --console=plain --no-configuration-cache 2>&1 |
        ForEach-Object {
            $line = "$_"
            Write-Host $line
            if ($line -match "^GitHub Packages credential source: (.+)$") {
                $script:credentialSource = $Matches[1]
            }
        }
    return $LASTEXITCODE -eq 0
}

Push-Location $repositoryRoot
try {
    Write-Host "Checking access to the configured Mindlayer and Tracebox packages..."
    if (Test-PackageAccess) {
        Write-Host "Ready. Gradle can download both dependencies."
        return
    }
    if ($CheckOnly) {
        throw "GitHub Packages access check failed. Follow the credential-source and recovery details above."
    }
    if (-not $script:credentialSource) {
        throw "Gradle failed before it could check authentication. Fix the build/configuration error above and rerun setup."
    }
    if ($script:credentialSource -ne "unavailable" -and $script:credentialSource -notlike "GitHub CLI*") {
        throw "Update or remove the explicit token from the credential source shown above. Signing in to gh cannot repair a Gradle/property/environment override."
    }

    $ghCommand = Get-Command gh -CommandType Application -ErrorAction SilentlyContinue
    $ghExecutable = if ($ghCommand) { $ghCommand.Source } else { $null }
    if (-not $ghExecutable) {
        foreach ($installRoot in @($env:ProgramFiles, $env:LOCALAPPDATA)) {
            if (-not $installRoot) { continue }
            foreach ($relativePath in @("GitHub CLI/gh.exe", "Programs/GitHub CLI/gh.exe")) {
                $candidate = Join-Path $installRoot $relativePath
                if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                    $ghExecutable = $candidate
                    break
                }
            }
            if ($ghExecutable) { break }
        }
    }
    if (-not $ghExecutable) {
        throw "Install GitHub CLI from https://cli.github.com/ and run this script again."
    }

    # Inspect only the active account. JSON status returns zero even for invalid
    # credentials, so inspect state too. Never include --show-token or echo output.
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $ghExecutable
    $startInfo.Arguments = "auth status --hostname github.com --active --json hosts"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    try {
        if (-not $process.WaitForExit(15000)) {
            $process.Kill()
            throw "GitHub CLI credential lookup timed out. Check access to its credential store and try again."
        }
        $account = $null
        if ($process.ExitCode -eq 0) {
            try {
                $status = $stdout.Result | ConvertFrom-Json
                $account = @($status.hosts.$githubHost | Where-Object { $_.active }) | Select-Object -First 1
            } catch {
                throw "GitHub CLI returned an unreadable authentication status. Update gh and try again."
            }
        }
    } finally {
        $process.Dispose()
    }

    if (-not $account -or $account.state -ne "success") {
        Write-Host "Sign in to GitHub and approve package-read access."
        & $ghExecutable auth login --hostname $githubHost --git-protocol https --web --scopes $requiredScope
    } elseif ($account.scopes -notmatch "(^|[^A-Za-z0-9_:])read:packages([^A-Za-z0-9_:]|$)") {
        Write-Host "The active GitHub login needs package-read permission."
        & $ghExecutable auth refresh --hostname $githubHost --scopes $requiredScope
    } else {
        throw "GitHub CLI has read:packages, but package access failed. Follow the registry error above; also check any GITHUB_USERNAME account selection."
    }
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub authentication did not complete successfully."
    }
    if (-not (Test-PackageAccess)) {
        throw "GitHub sign-in completed, but package access is still unavailable. Follow the registry error above."
    }
    Write-Host "Ready. Gradle can download both dependencies. The token remains in GitHub CLI's credential store."
} finally {
    Pop-Location
}
