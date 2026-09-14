<#
.SYNOPSIS
  PersonalAssistant 本地开发辅助脚本（PowerShell）。

.DESCRIPTION
  把「构建 / 安装 / 启动 / 看界面 / 抓日志」收进一条命令。
  SDK 路径里带单引号（JetBrain's），手敲 cmd 极易被吃掉，故统一走本脚本。

  注意：本文件必须以 UTF-8 with BOM 保存，否则 Windows PowerShell 5.1
  会按 GBK 解码，中文字符串会破坏引号配对导致解析失败。

.EXAMPLE
  ./scripts/local.ps1          # 构建 + 安装 + 启动 + 导出界面，一条龙
  ./scripts/local.ps1 Build    # 只 assembleDebug
  ./scripts/local.ps1 Run      # 只安装并启动（不重新构建）
  ./scripts/local.ps1 Ui       # 导出当前界面控件树，打印可见文字
  ./scripts/local.ps1 Log      # 抓本进程最近的告警/错误日志
  ./scripts/local.ps1 Test     # 跑 JVM 单元测试
  ./scripts/local.ps1 Device   # 列出设备与 AVD
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('All', 'Build', 'Install', 'Run', 'Ui', 'Tap', 'Walk', 'Shot', 'Log', 'Test', 'Device', 'Clean')]
    [string]$Action = 'All',

    # Tap 动作要点的文字或 content-desc
    [string]$Label = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------ 路径与常量
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Gradlew     = Join-Path $ProjectRoot 'gradlew.bat'
$ApkPath     = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$Artifacts   = Join-Path $ProjectRoot 'artifacts'

$Package   = 'com.example.personalassistant.debug'   # debug 带 applicationIdSuffix
$Component = 'com.example.personalassistant.debug/com.example.personalassistant.MainActivity'

$Backslash = [string][char]92

function Resolve-Sdk {
    foreach ($var in 'ANDROID_HOME', 'ANDROID_SDK_ROOT') {
        $v = [Environment]::GetEnvironmentVariable($var)
        if ($v -and (Test-Path $v)) { return $v }
    }
    # 退回读 local.properties（Java properties 格式：反斜杠被转义，可能是双重转义）
    $lp = Join-Path $ProjectRoot 'local.properties'
    if (Test-Path $lp) {
        $hit = Select-String -Path $lp -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
        if ($hit) {
            $raw = $hit.Matches[0].Groups[1].Value.Trim()
            $one = $Backslash
            $two = $Backslash + $Backslash
            $path = $raw.Replace($two, $one)      # \\ -> \
            $path = $path.Replace($one + ':', ':')  # \: -> :  (盘符冒号)
            if (Test-Path $path) { return $path }
        }
    }
    return $null
}

$Sdk = Resolve-Sdk
if (-not $Sdk) {
    throw '找不到可用的 Android SDK。请设置 ANDROID_HOME，或检查 local.properties 里的 sdk.dir。'
}

$Adb      = Join-Path $Sdk 'platform-tools\adb.exe'
$Emulator = Join-Path $Sdk 'emulator\emulator.exe'
if (-not (Test-Path $Adb)) { throw ("adb 不存在：" + $Adb) }

function Write-Step($msg) { Write-Host ''; Write-Host ("==== " + $msg + " ====") -ForegroundColor Cyan }

function Invoke-AdbRaw {
    param([string[]]$AdbArgs)
    return ((& $Adb @AdbArgs | Out-String).Trim())
}

# ------------------------------------------------------------ 设备
function Get-OnlineDevice {
    $out = Invoke-AdbRaw @('devices')
    $lines = $out -split "`n" | Select-Object -Skip 1 |
        Where-Object { $_ -match '\sdevice\s*$' -or $_ -match '\semulator\s*$' }
    if (-not $lines) { return $null }
    return (($lines[0] -split '\s+')[0])
}

function Invoke-Device {
    Write-Step '设备与模拟器'
    Write-Host '--- adb devices ---'
    Write-Host (Invoke-AdbRaw @('devices', '-l'))
    Write-Host '--- 已创建的 AVD ---'
    if (Test-Path $Emulator) {
        & $Emulator -list-avds
    } else {
        Write-Host '（emulator 未安装）' -ForegroundColor Yellow
    }
    Write-Host ('SDK: ' + $Sdk)
}

function Invoke-Ui {
    Write-Step '当前界面控件树'
    $nodes = Get-UiNodes
    $count = 0
    foreach ($n in $nodes) {
        $tag    = $n.Value
        $text   = [regex]::Match($tag, 'text="([^"]*)"').Groups[1].Value
        $desc   = [regex]::Match($tag, 'content-desc="([^"]*)"').Groups[1].Value
        $cls    = [regex]::Match($tag, 'class="([^"]*)"').Groups[1].Value
        $bounds = [regex]::Match($tag, 'bounds="([^"]*)"').Groups[1].Value
        $click  = [regex]::Match($tag, 'clickable="true"').Success
        if (-not $text -and -not $desc) { continue }
        $count++
        $label = if ($text) { $text } else { ('desc:' + $desc) }
        $mark  = if ($click) { ' [tap]' } else { '' }
        Write-Host ('  {0,-34} {1}{2}  {3}' -f $cls, $label, $mark, $bounds)
    }
    Write-Host ''
    Write-Host ('可见文字/描述节点：' + $count + ' 个；完整 XML：' + (Join-Path $Artifacts 'ui_dump.xml')) -ForegroundColor Green
}

function Get-UiNodes {
    param([int]$Retries = 4)
    if (-not (Test-Path $Artifacts)) { New-Item -ItemType Directory -Path $Artifacts | Out-Null }
    $local = Join-Path $Artifacts 'ui_dump.xml'

    # Compose 页面切换动画期间，uiautomator 会返回 "null root node"，
    # 此时设备上残留的是上一屏的旧 XML —— 必须重试并先删本地缓存，否则会读到脏数据。
    for ($i = 1; $i -le $Retries; $i++) {
        if (Test-Path $local) { Remove-Item $local -Force }
        Invoke-AdbRaw @('shell', 'rm', '-f', '/sdcard/ui_dump.xml') | Out-Null
        $dumped = Invoke-AdbRaw @('shell', 'uiautomator', 'dump', '/sdcard/ui_dump.xml')
        if ($dumped -match 'UI hierchary dumped') {
            Invoke-AdbRaw @('pull', '/sdcard/ui_dump.xml', $local) | Out-Null
            if (Test-Path $local) {
                $xml = [System.IO.File]::ReadAllText($local)
                if ($xml -match '<node\b') { return [regex]::Matches($xml, '<node\b[^>]*>') }
            }
        }
        Start-Sleep -Milliseconds 700
    }
    throw 'uiautomator dump 连续失败：界面可能一直在动画/渲染中'
}

function Find-NodeCenter {
    param([string]$Match)
    foreach ($n in (Get-UiNodes)) {
        $tag  = $n.Value
        $text = [regex]::Match($tag, 'text="([^"]*)"').Groups[1].Value
        $desc = [regex]::Match($tag, 'content-desc="([^"]*)"').Groups[1].Value
        if ($text -ne $Match -and $desc -ne $Match) { continue }
        $b = [regex]::Match($tag, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $b.Success) { continue }
        $cx = ([int]$b.Groups[1].Value + [int]$b.Groups[3].Value) / 2
        $cy = ([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2
        return [pscustomobject]@{ X = [int]$cx; Y = [int]$cy }
    }
    return $null
}

function Invoke-Tap {
    param([Parameter(Mandatory = $true)][string]$Label)
    Write-Step ("点击：" + $Label)
    $pt = Find-NodeCenter -Match $Label
    if (-not $pt) { throw ("当前界面上找不到文字/描述为『" + $Label + "』的控件；先跑 Ui 看看有什么。") }
    Invoke-AdbRaw @('shell', 'input', 'tap', [string]$pt.X, [string]$pt.Y) | Out-Null
    Write-Host ("已点击 ({0},{1})" -f $pt.X, $pt.Y)
    Start-Sleep -Milliseconds 900
    Invoke-Ui
}

function Invoke-Shot {
    Write-Step '截图'
    if (-not (Test-Path $Artifacts)) { New-Item -ItemType Directory -Path $Artifacts | Out-Null }
    $local = Join-Path $Artifacts 'screen.png'
    if (Test-Path $local) { Remove-Item $local -Force }
    Invoke-AdbRaw @('shell', 'screencap', '-p', '/sdcard/screen.png') | Out-Null
    Invoke-AdbRaw @('pull', '/sdcard/screen.png', $local) | Out-Null
    if (-not (Test-Path $local)) { throw '截图失败' }
    Write-Host ('截图已保存：' + $local) -ForegroundColor Green
}

# 遍历底部 4 个 Tab，逐屏导出可见文字，用于人工验收 UI 是否渲染
function Invoke-Walk {
    Write-Step '逐屏走查底部 Tab'
    foreach ($tab in '课表', '任务', '记账', '日历') {
        Write-Host ''
        Write-Host ('---------------- ' + $tab + ' ----------------') -ForegroundColor Magenta
        $pt = Find-NodeCenter -Match $tab
        if (-not $pt) { Write-Host ('  找不到 Tab『' + $tab + '』，跳过') -ForegroundColor Yellow; continue }
        Invoke-AdbRaw @('shell', 'input', 'tap', [string]$pt.X, [string]$pt.Y) | Out-Null
        Start-Sleep -Seconds 2
        $lines = @()
        foreach ($n in (Get-UiNodes)) {
            $text = [regex]::Match($n.Value, 'text="([^"]*)"').Groups[1].Value
            if ($text) { $lines += $text }
        }
        if ($lines.Count -eq 0) {
            Write-Host '  ! 该屏没有任何文字节点' -ForegroundColor Red
        } else {
            $lines | Select-Object -First 25 | ForEach-Object { Write-Host ('  ' + $_) }
            Write-Host ('  （共 ' + $lines.Count + ' 条文字）') -ForegroundColor DarkGray
        }
        $alive = Invoke-AdbRaw @('shell', 'pidof', $Package)
        if (-not $alive) { throw ('切到『' + $tab + '』后进程消失 —— 该屏渲染即崩溃') }
    }
    Write-Host ''
    Write-Host '四屏走查完毕，进程全程存活。' -ForegroundColor Green
}

function Invoke-Log {
    Write-Step '本应用日志（告警及以上）'
    $procId = Invoke-AdbRaw @('shell', 'pidof', $Package)
    if ($procId) {
        & $Adb logcat -d -v time ('--pid=' + $procId) '*:W' | Select-Object -Last 120
    } else {
        Write-Host ('进程未运行（' + $Package + '），改为按关键字抓最近错误：') -ForegroundColor Yellow
        & $Adb logcat -d -v time '*:E' | Select-String -Pattern 'personalassistant|AndroidRuntime|FATAL|Hilt|Room|SQLite' |
            Select-Object -Last 120
    }
}

function Invoke-Build {
    Write-Step '构建 assembleDebug'
    Push-Location $ProjectRoot
    try {
        & $Gradlew assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw ('Gradle 构建失败，退出码 ' + $LASTEXITCODE) }
    } finally { Pop-Location }
    $item = Get-Item $ApkPath
    Write-Host ('APK OK：{0}  {1:N1} MB  构建于 {2}' -f $item.Name, ($item.Length / 1MB), $item.LastWriteTime) -ForegroundColor Green
}

function Invoke-Install {
    Write-Step '安装到设备'
    if (-not (Test-Path $ApkPath)) { throw 'APK 不存在，先跑：./scripts/local.ps1 Build' }
    if (-not (Get-OnlineDevice)) { throw '没有在线设备。请在 Android Studio 的 Device Manager 里启动 pm_api34 模拟器' }
    & $Adb install -r $ApkPath
    if ($LASTEXITCODE -ne 0) { throw ('安装失败，退出码 ' + $LASTEXITCODE) }
}

function Invoke-Run {
    Write-Step '冷启动'
    Invoke-AdbRaw @('shell', 'am', 'force-stop', $Package) | Out-Null
    Invoke-AdbRaw @('logcat', '-c') | Out-Null
    Invoke-AdbRaw @('shell', 'am', 'start', '-W', '-n', $Component)
    Start-Sleep -Seconds 5

    $procId = Invoke-AdbRaw @('shell', 'pidof', $Package)
    if (-not $procId) {
        Write-Host '进程不在了 —— 启动即崩溃，下面贴日志：' -ForegroundColor Red
        Invoke-Log
        throw 'App 启动后崩溃'
    }
    Write-Host ('App 正在运行，PID=' + $procId) -ForegroundColor Green

    Write-Step '屏幕规格'
    $size = Invoke-AdbRaw @('shell', 'wm', 'size')
    $dens = Invoke-AdbRaw @('shell', 'wm', 'density')
    Write-Host $size
    Write-Host $dens
    $m = [regex]::Match($size, '(\d+)x(\d+)')
    if ($m.Success) {
        $w = [int]$m.Groups[1].Value
        $h = [int]$m.Groups[2].Value
        if ($w -lt 360 -or $h -lt 720) {
            Write-Host ('  ! 分辨率偏小（' + $w + 'x' + $h + '），低于 360x720。') -ForegroundColor Yellow
            Write-Host '    aosp_atd 是自动化测试机型，界面会被压缩，不适合人工验收 Compose 布局。' -ForegroundColor Yellow
            Write-Host '    建议在 Android Studio Device Manager 新建 412x915 / 1080x2400 的普通 Phone AVD。' -ForegroundColor Yellow
        }
    }
}

function Invoke-Test {
    Write-Step 'JVM 单元测试 testDebugUnitTest'
    Push-Location $ProjectRoot
    try {
        & $Gradlew testDebugUnitTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw ('单元测试失败，退出码 ' + $LASTEXITCODE) }
        Write-Host '单元测试通过' -ForegroundColor Green
    } finally { Pop-Location }
}

function Invoke-Clean {
    Write-Step '清理构建产物'
    Push-Location $ProjectRoot
    try { & $Gradlew clean --console=plain } finally { Pop-Location }
}

# ------------------------------------------------------------ 调度
if ($Action -in @('Ui', 'Log', 'Run', 'Tap', 'Walk', 'Shot') -and -not (Get-OnlineDevice)) {
    Write-Host ('SDK：' + $Sdk)
    throw '没有在线设备。先在 Android Studio 里启动模拟器（Device Manager -> pm_api34 -> Run），或手动跑 emulator -avd pm_api34'
}

switch ($Action) {
    'Device'  { Invoke-Device }
    'Build'   { Invoke-Build }
    'Install' { Invoke-Install }
    'Ui'      { Invoke-Ui }
    'Tap'     {
        if (-not $Label) { throw 'Tap 需要 -Label，例如：./scripts/local.ps1 Tap -Label 记账' }
        Invoke-Tap -Label $Label
    }
    'Walk'    { Invoke-Walk }
    'Shot'    { Invoke-Shot }
    'Log'     { Invoke-Log }
    'Test'    { Invoke-Test }
    'Clean'   { Invoke-Clean }
    'Run'     { Invoke-Install; Invoke-Run; Invoke-Ui }
    'All'     { Invoke-Build; Invoke-Install; Invoke-Run; Invoke-Ui; Invoke-Walk }
}

Write-Host '完成。' -ForegroundColor Green
