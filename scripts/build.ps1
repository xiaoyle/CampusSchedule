param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (Test-Path '.tools/jdk') { $env:JAVA_HOME = (Get-ChildItem '.tools/jdk' -Directory | Select-Object -First 1).FullName }
if (Test-Path '.tools/android-sdk') { $env:ANDROID_HOME = (Resolve-Path '.tools/android-sdk').Path }
if (-not (Test-Path '.tools/gradle-home')) { New-Item -ItemType Directory '.tools/gradle-home' -Force | Out-Null }
$env:GRADLE_USER_HOME = (Resolve-Path '.tools/gradle-home').Path
$gradle = if (Test-Path '.tools/gradle-8.11.1/bin/gradle.bat') { '.tools/gradle-8.11.1/bin/gradle.bat' } else { '.\gradlew.bat' }
if ($TestOnly) { & $gradle :core:test --console=plain } else { & $gradle :core:test :app:assembleDebug :app:lintDebug --console=plain }
if ($LASTEXITCODE -ne 0) { throw '编译或测试失败，请查看上方报告。' }
