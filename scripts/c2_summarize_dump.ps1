param(
    [string]$Dir = "D:\Projects\Code\Project-Source\ProfileManagement\docs\verified"
)
foreach ($f in @("c2-portrait.xml", "c2-landscape.xml")) {
    $p = Join-Path $Dir $f
    if (-not (Test-Path $p)) { Write-Output "MISSING: $f"; continue }
    $raw = Get-Content $p -Raw -Encoding UTF8
    $rot = [regex]::Match($raw, 'rotation="(\d+)"').Groups[1].Value
    $root = [regex]::Match($raw, '<hierarchy[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    $w = [int]$root.Groups[3].Value
    $h = [int]$root.Groups[4].Value
    $pkgs = [regex]::Matches($raw, 'package="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    $texts = [regex]::Matches($raw, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_.Trim().Length -gt 0 }
    Write-Output ("=== $f  rotation=$rot  root=$($w)x$($h)  textNodes=$($texts.Count)")
    Write-Output ("  pkgs = " + ($pkgs -join ", "))
    $i = 0
    foreach ($t in $texts) {
        if ($i -ge 16) { Write-Output ("  ... (+" + ($texts.Count - 16) + " more)"); break }
        Write-Output ("  - $t")
        $i++
    }
    Write-Output ""
}