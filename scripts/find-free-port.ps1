param(
    [int]$StartPort = 8080,
    [int]$EndPort = 8099
)

$ErrorActionPreference = 'SilentlyContinue'
$used = @{}
Get-NetTCPConnection -State Listen | ForEach-Object {
    $used[[int]$_.LocalPort] = $true
}

for ($port = $StartPort; $port -le $EndPort; $port++) {
    if (-not $used.ContainsKey($port)) {
        Write-Output $port
        exit 0
    }
}

exit 1
