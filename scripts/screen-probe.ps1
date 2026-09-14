<#
.SYNOPSIS
  客观判定当前设备画面是不是"白屏"——统计截图像素颜色分布，不依赖视觉模型。

.DESCRIPTION
  截图 -> 逐像素直方图 -> 输出：纯白/近白占比、唯一颜色数、平均亮度、四角取样。
  判据：
    - 近白占比 > 98% 且 唯一颜色数很小  => 设备画面确实是白屏
    - 唯一颜色数多、近白占比不高        => 画面有真实内容，白屏来自 AS 窗口本身

.EXAMPLE
  ./scripts/screen-probe.ps1
#>
[CmdletBinding()]
param(
    [int]$WhiteThreshold = 246,

    # 截图前先唤醒屏幕并解锁。headless(-no-window) 模拟器会熄屏，
    # 不唤醒直接 screencap 会得到全黑帧，误判成「渲染异常」。
    [switch]$Wake
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Artifacts   = Join-Path $ProjectRoot 'artifacts'
if (-not (Test-Path $Artifacts)) { New-Item -ItemType Directory -Path $Artifacts | Out-Null }
$Local = Join-Path $Artifacts 'probe.png'

$Backslash = [string][char]92

function Resolve-Sdk {
    foreach ($var in 'ANDROID_HOME', 'ANDROID_SDK_ROOT') {
        $v = [Environment]::GetEnvironmentVariable($var)
        if ($v -and (Test-Path $v)) { return $v }
    }
    $lp = Join-Path $ProjectRoot 'local.properties'
    if (Test-Path $lp) {
        $hit = Select-String -Path $lp -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
        if ($hit) {
            $raw = $hit.Matches[0].Groups[1].Value.Trim()
            $one = $Backslash
            $path = $raw.Replace($one + $one, $one).Replace($one + ':', ':')
            if (Test-Path $path) { return $path }
        }
    }
    return $null
}

$Sdk = Resolve-Sdk
if (-not $Sdk) { throw '找不到 Android SDK' }
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'

function AdbRaw {
    param([string[]]$AdbArgs)
    return ((& $Adb @AdbArgs | Out-String).Trim())
}

if (-not (AdbRaw @('devices')) -match '\sdevice\s*$') { throw '没有在线设备' }

if ($Wake) {
    Write-Host ''
    Write-Host '==== 唤醒屏幕 ====' -ForegroundColor Cyan
    AdbRaw @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    AdbRaw @('shell', 'wm', 'dismiss-keyguard') | Out-Null
    # 拉长熄屏时间，避免下一次截图又抓到熄屏帧
    AdbRaw @('shell', 'settings', 'put', 'system', 'screen_off_timeout', '900000') | Out-Null
    Start-Sleep -Seconds 2
    $power = AdbRaw @('shell', 'dumpsys', 'power')
    $wakeState = [regex]::Match($power, 'mWakefulness=(\w+)').Groups[1].Value
    Write-Host ('  mWakefulness = ' + $wakeState)
}

if (Test-Path $Local) { Remove-Item $Local -Force }
AdbRaw @('shell', 'screencap', '-p', '/sdcard/probe.png') | Out-Null
AdbRaw @('pull', '/sdcard/probe.png', $Local) | Out-Null
if (-not (Test-Path $Local)) { throw '截图失败' }

$bmp = [System.Drawing.Bitmap]::new($Local)
try {
    $w = $bmp.Width
    $h = $bmp.Height
    $nearWhite = 0
    $total     = 0
    $lumSum    = 0.0
    $distinct  = [System.Collections.Generic.HashSet[int]]::new()

    # 步进采样，320x640 也够密
    $stepX = [Math]::Max(1, [int]($w / 160))
    $stepY = [Math]::Max(1, [int]($h / 320))

    for ($y = 0; $y -lt $h; $y += $stepY) {
        for ($x = 0; $x -lt $w; $x += $stepX) {
            $c = $bmp.GetPixel($x, $y)
            $total++
            [void]$distinct.Add(($c.R -shl 16) -bor ($c.G -shl 8) -bor $c.B)
            if ($c.R -ge $WhiteThreshold -and $c.G -ge $WhiteThreshold -and $c.B -ge $WhiteThreshold) {
                $nearWhite++
            }
            $lumSum += (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B)
        }
    }

    $whitePct = $nearWhite * 100.0 / $total
    $avgLum   = $lumSum / $total

    Write-Host ''
    Write-Host '==== 画面像素分析 ====' -ForegroundColor Cyan
    Write-Host ('截图尺寸      : {0}x{1}' -f $w, $h)
    Write-Host ('采样点        : ' + $total)
    Write-Host ('唯一颜色数    : ' + $distinct.Count)
    Write-Host ('近白占比(>=' + $WhiteThreshold + ') : {0:N2} %' -f $whitePct)
    Write-Host ('平均亮度      : {0:N1} / 255' -f $avgLum)

    foreach ($corner in @(
        @{ N = '左上'; X = 8; Y = 8 },
        @{ N = '中上'; X = [int]($w / 2); Y = 8 },
        @{ N = '中心'; X = [int]($w / 2); Y = [int]($h / 2) },
        @{ N = '中下'; X = [int]($w / 2); Y = $h - 40 },
        @{ N = '右下'; X = $w - 8; Y = $h - 8 }
    )) {
        $c = $bmp.GetPixel($corner.X, $corner.Y)
        Write-Host ('  {0,-4} ({1,3},{2,3}) = #{3:X2}{4:X2}{5:X2}' -f `
            $corner.N, $corner.X, $corner.Y, $c.R, $c.G, $c.B)
    }

    Write-Host ''
    if ($distinct.Count -le 2) {
        # 全黑或全单色：拿到的不是真实画面。最常见原因是 -no-window 无头模拟器
        # 没有渲染表面，screencap 只能得到空帧。不能拿它判断「有没有白屏」。
        $mono = if ($nearWhite -eq $total) { '纯白' } else { '单色' }
        Write-Host ('判定：截图为' + $mono + '帧（唯一颜色 ' + $distinct.Count + ' 个）—— 截图无效，不能据此判断白屏。') -ForegroundColor Red
        Write-Host '      无头(-no-window) 模拟器没有渲染表面，请用带窗口模式跑本脚本。' -ForegroundColor Yellow
    } elseif ($whitePct -gt 98.0) {
        Write-Host '判定：设备帧缓冲本身就是接近纯白的画面 —— 真·白屏。' -ForegroundColor Red
    } else {
        Write-Host '判定：设备帧缓冲有真实内容，App 渲染正常，不是白屏。' -ForegroundColor Green
        Write-Host '      若 Android Studio 里的模拟器窗口仍显示白色，问题在那个窗口的渲染表面，不在 App。' -ForegroundColor Yellow
    }
} finally {
    $bmp.Dispose()
}

Write-Host ''
Write-Host ('截图：' + $Local) -ForegroundColor DarkGray
