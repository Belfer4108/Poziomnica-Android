param(
    [string]$TempRoot = "C:\tmp",
    [string]$ProjectName = "Poziomnica-test"
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$tempProject = Join-Path $TempRoot $ProjectName
$gradleHome = Join-Path $TempRoot "gradle-home-poziomnica"

if (-not (Test-Path -LiteralPath $TempRoot)) {
    New-Item -ItemType Directory -Path $TempRoot | Out-Null
}

if (Test-Path -LiteralPath $tempProject) {
    Remove-Item -LiteralPath $tempProject -Recurse -Force
}

Copy-Item -LiteralPath $projectRoot -Destination $tempProject -Recurse -Force

if (-not (Test-Path -LiteralPath $gradleHome)) {
    New-Item -ItemType Directory -Path $gradleHome | Out-Null
}

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:GRADLE_USER_HOME = $gradleHome
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

Push-Location $tempProject
try {
    .\gradlew.bat :app:testDebugUnitTest --no-daemon
} finally {
    Pop-Location
}
