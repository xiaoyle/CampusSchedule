$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$output = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force $output | Out-Null
Copy-Item 'app/build/outputs/apk/debug/app-debug.apk' (Join-Path $output 'CampusSchedule-0.10.0.apk') -Force
Copy-Item 'README.md' (Join-Path $output '安装与使用说明.md') -Force
Copy-Item 'VERIFICATION.md' (Join-Path $output '验证记录.md') -Force
Add-Type -AssemblyName System.IO.Compression
$archivePath = Join-Path $output 'CampusSchedule-source-0.10.0.zip'
$stream = [System.IO.File]::Open($archivePath, [System.IO.FileMode]::Create)
$archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = @('README.md','VERIFICATION.md','DESIGN.md','UX-CONTRACT.md','CHANGELOG.md','CONTRIBUTING.md','.gitignore','settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts','core/build.gradle.kts','web/.gitignore','web/browser-check.mjs','web/DESIGN.md','web/index.html','web/package-lock.json','web/package.json','web/pdf-debug.mjs','web/premium-ui.json','web/prepare-assets.mjs','web/public-check.mjs','web/public-debug.mjs','web/README.md','web/sw-debug.mjs','web/tsconfig.json','web/vite.config.ts')
    foreach($folder in @('app/src','core/src','gradle','scripts','docs','.github','web/src','web/tests','web/public')) {
        $files += Get-ChildItem -LiteralPath $folder -Recurse -File | ForEach-Object { [System.IO.Path]::GetRelativePath($projectRoot, $_.FullName) }
    }
    foreach($file in $files) {
        $entry = $archive.CreateEntry($file.Replace('\','/'))
        $entryStream = $entry.Open()
        try { $bytes = [System.IO.File]::ReadAllBytes((Join-Path $projectRoot $file)); $entryStream.Write($bytes,0,$bytes.Length) }
        finally { $entryStream.Dispose() }
    }
} finally { $archive.Dispose(); $stream.Dispose() }
Get-FileHash -Algorithm SHA256 (Join-Path $output 'CampusSchedule-0.10.0.apk'),$archivePath | ForEach-Object { "$($_.Hash)  $([System.IO.Path]::GetFileName($_.Path))" } | Set-Content -LiteralPath (Join-Path $output 'SHA256SUMS-0.10.0.txt') -Encoding utf8
Get-ChildItem $output | Select-Object Name,Length
