[CmdletBinding()]
param(
  [Parameter(Mandatory=$false)][int]$Port = 8080,
  [switch]$Quiet
)

$ErrorActionPreference = "Stop"

function Get-PIDsOnPort {
  param([int]$Port)
  try {
    $pids = @()
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
      $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
      if ($conns) { $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique }
    }
    if (-not $pids -or $pids.Count -eq 0) {
      $lines = netstat -ano | Select-String -Pattern (":$Port\s")
      foreach ($line in $lines) {
        $parts = $line.ToString() -split '\s+'
        if ($parts.Length -ge 5) {
          $pid = $parts[-1]
          if ($pid -match '^[0-9]+$') { $pids += [int]$pid }
        }
      }
      $pids = $pids | Select-Object -Unique
    }
    return $pids
  } catch {
    Write-Verbose $_
    return @()
  }
}

$pids = Get-PIDsOnPort -Port $Port
if (-not $pids -or $pids.Count -eq 0) {
  if (-not $Quiet) { Write-Host "No process found listening on port $Port." -ForegroundColor Yellow }
  exit 0
}

foreach ($procId in $pids) {
  try {
  $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
  if (-not $Quiet) { Write-Host "Stopping PID ${procId} ($($proc.ProcessName)) on port $Port..." -ForegroundColor Cyan }
  Stop-Process -Id $procId -Force -ErrorAction Stop
  if (-not $Quiet) { Write-Host "Stopped PID ${procId}." -ForegroundColor Green }
  } catch {
  if (-not $Quiet) { Write-Warning "Failed to stop PID ${procId}: $($_.Exception.Message)" }
  }
}
