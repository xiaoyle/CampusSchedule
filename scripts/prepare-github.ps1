$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$version = '0.3.0'
$apk = Join-Path $projectRoot "dist/CampusSchedule-$version.apk"
if (-not (Test-Path -LiteralPath $apk)) { throw '请先构建并打包 APK，再准备 GitHub 发布目录。' }
$destination = Join-Path $projectRoot ('dist/github-release-' + $version + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $destination) { throw '目标已存在，请稍后重试；不会覆盖旧准备目录。' }
$repository = Join-Path $destination 'repository'
$assets = Join-Path $destination 'release-assets'
New-Item -ItemType Directory -Path $repository,$assets | Out-Null
# Explicit allowlist: do not enumerate the workspace recursively.
$files = @('README.md','VERIFICATION.md','DESIGN.md','UX-CONTRACT.md','CHANGELOG.md','CONTRIBUTING.md','.gitignore','settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts','core/build.gradle.kts')
foreach ($folder in @('app/src','core/src','gradle','scripts','docs','.github')) {
    $files += Get-ChildItem -LiteralPath (Join-Path $projectRoot $folder) -Recurse -File | ForEach-Object {
        [System.IO.Path]::GetRelativePath($projectRoot,$_.FullName)
    }
}
foreach ($relative in $files) {
    if ($relative -match '(?i)(private-fixtures|(^|[\\/])build[\\/]|\.(jks|keystore|p12|pfx|pem|key|apk|aab)$)') { throw "不允许公开的文件类型：$relative" }
    $target = Join-Path $repository $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot $relative) -Destination $target
}
Copy-Item -LiteralPath $apk -Destination $assets
Copy-Item -LiteralPath (Join-Path $projectRoot "docs/RELEASE-v$version.md") -Destination (Join-Path $destination '发布说明.md')
$sourceZip = Join-Path $assets "CampusSchedule-source-$version.zip"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($repository,$sourceZip)
Get-FileHash -Algorithm SHA256 (Join-Path $assets "CampusSchedule-$version.apk"),$sourceZip | ForEach-Object {
    "$($_.Hash)  $([System.IO.Path]::GetFileName($_.Path))"
} | Set-Content -LiteralPath (Join-Path $assets "SHA256SUMS-$version.txt") -Encoding utf8
Write-Output "发布准备目录：$destination"
Write-Output "源码文件数：$($files.Count)"
Write-Output '已准备文件；尚未创建 GitHub 仓库，也未上传或发布。'
