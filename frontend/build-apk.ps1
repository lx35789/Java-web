# 植语助手 · Android APK 一键打包
#
# 用法（在本目录下）：
#   .\build-apk.ps1                 # 用 .env.android 里的默认后端地址打包
#   .\build-apk.ps1 -ApiBase http://192.168.1.10:8080   # 临时指定后端地址
#   .\build-apk.ps1 -Release        # 打 release 包（需先配签名）
#
# 为什么不用全局 JAVA_HOME：
#   后端（Spring Boot）编译要 JDK 17，Android（AGP 8.13）要 JDK 21。
#   本脚本只在运行时把 JDK 21 塞给 Gradle，不污染系统环境变量。

param(
    [string]$ApiBase = "",
    [switch]$Release
)

# 用 Continue + 显式检查 $LASTEXITCODE：
#   java / gradlew / npm 都会往 stderr 写正常输出（如版本号、Gradle 进度），
#   设为 Stop 时 PowerShell 5.1 会把它们当成 NativeCommandError 直接终止脚本。
$ErrorActionPreference = "Continue"
$script:Failed = $false

function CheckExit($what) {
    if ($LASTEXITCODE -ne 0) { throw "$what 失败（exit=$LASTEXITCODE）" }
}

# ---- 路径（换电脑只需改这三行）----
$JdkHome    = "C:\dev\jdk-21"
$AndroidSdk = "C:\dev\android-sdk"

$Here = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $Here

function Step($n, $t) { Write-Host "`n[$n] $t" -ForegroundColor Cyan }

# ---- 0. 环境自检 ----
Step 0 "环境自检"
if (-not (Test-Path "$JdkHome\bin\javac.exe")) {
    throw "找不到 JDK 21：$JdkHome`n请修改脚本第 17 行的 `$JdkHome"
}
if (-not (Test-Path "$AndroidSdk\platforms\android-36")) {
    throw "找不到 Android SDK platform-36：$AndroidSdk`n请修改脚本第 18 行的 `$AndroidSdk"
}
$env:JAVA_HOME = $JdkHome
$v = (& "$JdkHome\bin\java.exe" -version 2>&1 | Select-Object -First 1) -join ''
Write-Host "    $v"
Write-Host "    SDK: $AndroidSdk"

# local.properties 已 gitignore，每次确保存在（用正斜杠，反斜杠会被 properties 当转义）
"sdk.dir=$($AndroidSdk -replace '\\','/')" | Set-Content -Path "android\local.properties" -Encoding ASCII

# ---- 1. 依赖 ----
if (-not (Test-Path "node_modules\@capacitor\core")) {
    Step 1 "安装 npm 依赖"
    npm install --no-audit --no-fund
    CheckExit "npm install"
} else {
    Step 1 "依赖已就绪，跳过安装"
}

# ---- 2. 构建前端（android 模式 = 绝对地址）----
Step 2 "构建前端 (vite --mode android)"
npm run build -- --mode android
CheckExit "前端构建"

if ($ApiBase) {
    Step "2b" "把 dist 里的默认后端地址替换为 $ApiBase"
    $norm = $ApiBase.TrimEnd('/')
    if ($norm -notmatch '/api/v1$') { $norm = "$norm/api/v1" }
    if ($norm -notmatch '^https?://') { $norm = "http://$norm" }
    Get-ChildItem "dist\assets\*.js" | ForEach-Object {
        $c = Get-Content $_.FullName -Raw -Encoding UTF8
        if ($c -match 'https?://[^"'']+/api/v1') {
            $c = [regex]::Replace($c, 'https?://[^"'']+/api/v1', $norm)
            Set-Content $_.FullName -Value $c -Encoding UTF8 -NoNewline
        }
    }
    Write-Host "    -> $norm"
}

# ---- 3. 同步到 Android 工程 ----
Step 3 "cap sync android"
npx cap sync android
CheckExit "cap sync"

# ---- 4. 编译 ----
$task = if ($Release) { "assembleRelease" } else { "assembleDebug" }
Step 4 "gradlew $task"
Set-Location android
& ".\gradlew.bat" $task --no-daemon
CheckExit "Gradle 编译"
Set-Location $Here

# ---- 5. 产出 ----
Step 5 "产物"
$apk = Get-ChildItem -Path "android\app\build\outputs\apk" -Recurse -Filter "*.apk" |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "没有找到 APK" }
$mb = [math]::Round($apk.Length / 1MB, 1)
Write-Host "    $($apk.FullName)" -ForegroundColor Green
Write-Host "    $mb MB"

# 顺手拷一份到桌面，方便发手机
$deskDir = [Environment]::GetFolderPath("Desktop")
if (Test-Path $deskDir) {
    $dest = Join-Path $deskDir $apk.Name
    Copy-Item $apk.FullName $dest -Force
    Write-Host "    已复制到：$dest" -ForegroundColor Green
}

Write-Host "`n完成。安装：adb install -r `"$($apk.FullName)`"" -ForegroundColor Green
