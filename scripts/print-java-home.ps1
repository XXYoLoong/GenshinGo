$ErrorActionPreference = 'Stop'
$out = cmd /c 'java -XshowSettings:properties -version 2>&1' 2>&1 | Out-String
if ($out -match 'java\.home = (.+)') {
    $home = $matches[1].Trim()
    if (Test-Path (Join-Path $home 'bin\java.exe')) {
        Write-Output $home
        exit 0
    }
}
exit 1
