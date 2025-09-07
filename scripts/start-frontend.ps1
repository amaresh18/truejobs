[CmdletBinding()]
param(
  [int]$Port = 3000
)
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $repoRoot 'frontend'

Push-Location $frontend
try {
  if (-not (Test-Path 'node_modules')) {
    Write-Host 'Installing frontend dependencies...' -ForegroundColor Cyan
    npm install
  }
  Write-Host "Starting frontend on port $Port..." -ForegroundColor Green
  $env:PORT = "$Port"
  npm run dev
} finally {
  Pop-Location
}
