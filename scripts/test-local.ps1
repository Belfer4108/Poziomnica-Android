param(
    [string]$AsciiWorkspace = "C:\tmp\PoziomnicaTest"
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$hasNonAsciiPath = $root.Path.ToCharArray() | Where-Object { [int][char]$_ -gt 127 } | Select-Object -First 1

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:GRADLE_USER_HOME = "C:\tmp\gradle-home"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

if ($hasNonAsciiPath) {
    if (Test-Path -LiteralPath $AsciiWorkspace) {
        git -C $AsciiWorkspace fetch "$root" HEAD
        git -C $AsciiWorkspace reset --hard FETCH_HEAD
    } else {
        git clone "$root" $AsciiWorkspace
    }
    Push-Location $AsciiWorkspace
} else {
    Push-Location $root
}

try {
    .\gradlew.bat testDebugUnitTest --no-daemon
} finally {
    Pop-Location
}
