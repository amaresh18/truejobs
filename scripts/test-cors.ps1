[CmdletBinding()]
param(
  [string]$BaseUrl = 'http://localhost:8080',
  [string]$Origin = 'http://192.168.1.9:3000'
)
$ErrorActionPreference = 'Stop'

$headers = @{ Origin = $Origin; 'Access-Control-Request-Method' = 'GET'; 'Access-Control-Request-Headers' = 'authorization,content-type' }
try {
  $resp = Invoke-WebRequest -Method Options -Uri "$BaseUrl/api/jobs" -Headers $headers -UseBasicParsing
  Write-Host "Preflight status: $($resp.StatusCode)" -ForegroundColor Green
  $resp.Headers | Format-List | Out-String | Write-Host
} catch {
  $code = $_.Exception.Response.StatusCode.Value__
  $desc = $_.Exception.Response.StatusDescription
  Write-Host "Preflight failed: $code $desc" -ForegroundColor Red
}
