# Starts the ReconIQ backend, loading secrets from .env (git-ignored).
#
#   .\run.ps1              # MySQL (uses .env)
#   .\run.ps1 -H2          # in-memory H2, no MySQL needed
#   .\run.ps1 -Rebuild     # package the jar first, then start
#
# Stop a running backend first (Ctrl+C in its window).

param(
    [switch]$H2,
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$jar = Join-Path $root "backend\target\reconiq-backend-1.0.0.jar"

# --- load .env ---------------------------------------------------------
$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
    Write-Host "Loaded settings from .env" -ForegroundColor DarkGray
} else {
    Write-Warning "No .env found - using defaults (root/empty password, email disabled)."
}

# --- report what will run ---------------------------------------------
if ($env:MAIL_USER -and $env:MAIL_PASS) {
    Write-Host "Email PDF delivery: ENABLED ($($env:MAIL_USER))" -ForegroundColor Green
} else {
    Write-Host "Email PDF delivery: disabled (set MAIL_USER / MAIL_PASS in .env)" -ForegroundColor Yellow
}

if ($H2) {
    Write-Host "Database: in-memory H2 (demo data resets on restart)" -ForegroundColor Cyan
} else {
    Write-Host "Database: MySQL $($env:DB_URL -replace '\?.*$', '')" -ForegroundColor Cyan
}

# --- package if needed -------------------------------------------------
if ($Rebuild -or -not (Test-Path $jar)) {
    Write-Host "Packaging backend..." -ForegroundColor DarkGray
    Push-Location (Join-Path $root "backend")
    try {
        mvn -q -Ph2 -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "mvn package failed (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

# --- run ---------------------------------------------------------------
# Run from backend/ with a relative jar path: this project lives under a
# non-ASCII folder (ドキュメント) and Java cannot open absolute paths containing it.
$backend = Join-Path $root "backend"
Push-Location $backend
try {
    $arguments = @("-jar", "target\reconiq-backend-1.0.0.jar")
    if ($H2) { $arguments += "--spring.profiles.active=h2" }

    Write-Host "Starting ReconIQ on http://localhost:8080" -ForegroundColor Cyan
    & java @arguments
} finally {
    Pop-Location
}
