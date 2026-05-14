param(
    [Parameter(Mandatory = $true)]
    [int]$Port
)

$ErrorActionPreference = 'SilentlyContinue'
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen
if ($listener) {
    exit 1
}

exit 0
