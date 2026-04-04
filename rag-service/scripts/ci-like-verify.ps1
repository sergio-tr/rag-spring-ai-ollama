# Replicate .github/workflows/ci.yml backend Postgres + env, then run mvn verify.
# Requires: Docker Desktop, JDK 21. Run from repository root:
#   .\rag-service\scripts\ci-like-verify.ps1
# Optional: -StopAfter, -PrepareOnly

[CmdletBinding()]
param(
    [switch] $StopAfter,
    [switch] $PrepareOnly
)

$ErrorActionPreference = "Stop"

$ContainerName = if ($env:RAG_CI_POSTGRES_CONTAINER) { $env:RAG_CI_POSTGRES_CONTAINER } else { "rag-ci-postgres" }
$Image = "pgvector/pgvector:pg16"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RagService = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $RagService "..")
$TestInit = Join-Path $RagService "src\test\resources\test-init.sql"
$CiExtensions = Join-Path $ScriptDir "ci-postgres-extensions.sql"

if (-not (Test-Path -LiteralPath $TestInit)) {
    Write-Error "test-init.sql not found: $TestInit"
}
if (-not (Test-Path -LiteralPath $CiExtensions)) {
    Write-Error "ci-postgres-extensions.sql not found: $CiExtensions"
}

docker info 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker is not running or not in PATH."
}

function Wait-PostgresReady {
    param([int] $MaxAttempts = 40)
    for ($i = 0; $i -lt $MaxAttempts; $i++) {
        docker exec $ContainerName pg_isready -U postgres -d vectordb 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    Write-Error "Postgres did not become ready in time."
}

function Assert-DockerExit {
    param([string] $Step)
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker step failed ($Step), exit code $LASTEXITCODE"
    }
}

$existing = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq $ContainerName }
if ($existing) {
    $running = docker ps --format "{{.Names}}" | Where-Object { $_ -eq $ContainerName }
    if ($running) {
        Write-Host "Using existing running container: $ContainerName"
    }
    else {
        Write-Host "Starting existing container: $ContainerName"
        docker start $ContainerName
    }
}
else {
    Write-Host "Creating container $ContainerName ($Image) on port 5432..."
    docker run -d --name $ContainerName `
        -e POSTGRES_USER=postgres `
        -e POSTGRES_PASSWORD=postgres `
        -e POSTGRES_DB=vectordb `
        -p 5432:5432 `
        $Image
}

Wait-PostgresReady

Write-Host "Creating extensions on vectordb (same as CI, via SQL file - reliable on Windows)..."
docker cp $CiExtensions "${ContainerName}:/tmp/ci-extensions.sql"
Assert-DockerExit "docker cp ci-extensions.sql"
docker exec $ContainerName psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -f /tmp/ci-extensions.sql
Assert-DockerExit "psql ci-extensions.sql on vectordb"

Write-Host "Ensuring testdb + test-init.sql..."
$dbExists = docker exec $ContainerName psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'testdb'"
Assert-DockerExit "list databases"
if (-not (($dbExists | Out-String).Trim() -match "1")) {
    docker exec $ContainerName psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;"
    Assert-DockerExit "CREATE DATABASE testdb"
}

docker cp $TestInit "${ContainerName}:/tmp/test-init.sql"
Assert-DockerExit "docker cp test-init.sql"
docker exec $ContainerName psql -U postgres -d testdb -v ON_ERROR_STOP=1 -f /tmp/test-init.sql
Assert-DockerExit "psql test-init.sql on testdb"

if ($PrepareOnly) {
    Write-Host "PrepareOnly: done. Run verify manually with CI env vars (see rag-service/README.md)."
    exit 0
}

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/vectordb"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "postgres"
$env:RAG_JWT_SECRET = "test-secret-key-for-jwt-signing-must-be-long-enough-32"
$env:RAG_TEST_USE_TESTCONTAINERS_DATASOURCE = "false"
$env:INTEGRATION_JDBC_URL = "jdbc:postgresql://localhost:5432/testdb"

Push-Location $RagService
try {
    & .\mvnw.cmd -B clean verify
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}

if ($StopAfter) {
    Write-Host "Removing container $ContainerName..."
    docker rm -f $ContainerName | Out-Null
}
