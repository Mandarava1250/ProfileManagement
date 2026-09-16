param(
    [string]$Dir = "D:\Projects\Code\Project-Source\ProfileManagement\docs\verified"
)
$files = Get-ChildItem -Path $Dir -Filter "c1-ui-*.xml"
foreach ($f in $files) {
    $raw = Get-Content -Path $f.FullName -Raw -Encoding UTF8
    $matches = [regex]::Matches($raw, 'text="([^"]+)"')
    $texts = @()
    foreach ($m in $matches) {
        $t = $m.Groups[1].Value
        if ($t.Trim().Length -gt 0) { $texts += $t }
    }
    $pkg = [regex]::Match($raw, 'package="([^"]+)"').Groups[1].Value
    Write-Output "=== $($f.Name) | nodes_text=$($texts.Count) | top_pkg=$pkg"
    $i = 0
    foreach ($t in $texts) {
        if ($i -ge 30) { Write-Output "  ... (+$($texts.Count - 30) more)"; break }
        Write-Output "  - $t"
        $i++
    }
    Write-Output ""
}