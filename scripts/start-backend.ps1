[CmdletBinding()]
param(
  [switch]$Clean,
  [int]$Port = 8081
)
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $repoRoot 'backend'

# Ensure JAVA_HOME for JDK 21 if not set
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
  $jdk = 'C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'
  if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
}

# Free port
& (Join-Path $repoRoot 'scripts\kill-port.ps1') -Port $Port -Quiet

Push-Location $backend
try {
  if ($Clean) {
    Write-Host 'Cleaning backend...' -ForegroundColor Cyan
    cmd /c .\gradlew.bat clean -q
  }
  Write-Host 'Building backend...' -ForegroundColor Cyan
  cmd /c .\gradlew.bat build -x test
  Write-Host "Starting backend on port $Port..." -ForegroundColor Green
  $env:SERVER_PORT = "$Port"
  cmd /c .\gradlew.bat bootRun
} finally {
  Pop-Location
}
