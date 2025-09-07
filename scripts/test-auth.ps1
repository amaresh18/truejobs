[CmdletBinding()]
param(
  [string]$BaseUrl = 'http://localhost:8081'
)
$ErrorActionPreference = 'Stop'

function Invoke-Json {
  param(
    [string]$Method,
    [string]$Url,
    [object]$Body,
    [hashtable]$Headers
  )
  $jsonBody = $null
  if ($Body) { $jsonBody = $Body | ConvertTo-Json }
  return Invoke-RestMethod -Method $Method -Uri $Url -ContentType 'application/json' -Body $jsonBody -Headers $Headers
}

Write-Host "Registering recruiter..." -ForegroundColor Cyan
$reg = Invoke-Json -Method Post -Url "$BaseUrl/api/auth/register" -Body @{ name='Recruiter QA'; email='recruiter.qa@example.com'; password='password123'; role='RECRUITER' }
$reg | ConvertTo-Json -Depth 5

Write-Host "Logging in..." -ForegroundColor Cyan
$login = Invoke-Json -Method Post -Url "$BaseUrl/api/auth/login" -Body @{ email='recruiter.qa@example.com'; password='password123' }
$login | ConvertTo-Json -Depth 5

$headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host "GET /api/auth/me" -ForegroundColor Cyan
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/auth/me" -Headers $headers | ConvertTo-Json -Depth 5

Write-Host "GET /api/applications/recruiter" -ForegroundColor Cyan
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/applications/recruiter" -Headers $headers | ConvertTo-Json -Depth 5
