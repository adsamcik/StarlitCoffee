# Find asset-delivery classes.jar
$gradleCache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"

Write-Host "Searching in: $gradleCache"

# Method 1: Direct path
$paths = @(
    "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.android.play.core\play-core",
    "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.android.play.core\play-core-ktx",
    "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.android.play.core"
)

foreach($path in $paths) {
    if(Test-Path $path) {
        Write-Host "Found: $path"
        Get-ChildItem -Path $path -Recurse -Filter "classes.jar" | ForEach-Object {
            Write-Host "  -> $_"
        }
    }
}

# Try finding any classes.jar with asset in path
Write-Host "`nSearching for any classes.jar..."
Get-ChildItem -Path $gradleCache -Recurse -Filter "classes.jar" -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -like "*play*core*" -and $_.FullName -notlike "*ktx*"
} | Select-Object -First 3 | ForEach-Object {
    Write-Host $_.FullName
}
