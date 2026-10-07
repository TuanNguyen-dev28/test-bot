$ErrorActionPreference = 'Stop'
$fabricWorkspace = Split-Path -Parent $PSScriptRoot
$fabricCache = Join-Path $fabricWorkspace '.cache'
$previousMavenHome = $env:MAVEN_USER_HOME
$previousDataDir = $env:FABRIC_DATA_DIR
try {
    $env:MAVEN_USER_HOME = Join-Path $fabricCache 'maven-user'
    $env:FABRIC_DATA_DIR = Join-Path $fabricWorkspace 'data'
    Push-Location (Join-Path $PSScriptRoot 'back-end')
    try {
        & .\mvnw.cmd "-Dmaven.repo.local=$fabricCache/m2" spring-boot:run
        $fabricExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $env:MAVEN_USER_HOME = $previousMavenHome
    $env:FABRIC_DATA_DIR = $previousDataDir
}
exit $fabricExitCode
