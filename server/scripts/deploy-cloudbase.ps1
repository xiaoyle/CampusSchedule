param(
    [Parameter(Mandatory=$true)][string]$EnvironmentId
)
$ErrorActionPreference='Stop'
$serverRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $serverRoot
if(-not (Get-Command tcb -ErrorAction SilentlyContinue)){throw '未找到 CloudBase CLI。请先运行 npm install -g @cloudbase/cli。'}
if(-not (Test-Path -LiteralPath 'Dockerfile')){throw '当前目录缺少 Dockerfile。'}
Write-Output '将打开 CloudBase 授权并部署 server/Dockerfile。数据库密码和 JWT_SECRET 请在 CloudRun 控制台环境变量中配置。'
& tcb env use $EnvironmentId
if($LASTEXITCODE -ne 0){throw '选择 CloudBase 环境失败。'}
& tcb cloudrun deploy --env-id $EnvironmentId
if($LASTEXITCODE -ne 0){throw 'CloudRun 部署失败，请查看上方日志。'}
