[CmdletBinding()]
param(
    [switch]$BootstrapAdmin,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$NativeAuthDirectory
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if (!$JavaHome) { $JavaHome = Join-Path $env:USERPROFILE '.jdks/temurin-21.0.10' }
$java = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $java)) { throw 'Set JAVA_HOME to JDK 21 or pass -JavaHome.' }
if (!$NativeAuthDirectory) { $NativeAuthDirectory = Join-Path $repoRoot 'target/jdbc-auth-12.10.1/x64' }
if (!(Test-Path -LiteralPath (Join-Path $NativeAuthDirectory 'mssql-jdbc_auth-12.10.1.x64.dll'))) {
    throw 'Pass -NativeAuthDirectory containing mssql-jdbc_auth-12.10.1.x64.dll.'
}
$jar = Join-Path $repoRoot 'target/uteb-burrito-1.0.0.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Build first: .\mvnw.cmd package' }
$env:SPRING_PROFILES_ACTIVE = 'sqlserver'
$env:DB_AUTH = 'windows'
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=UTE_Burrito;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true'
Remove-Item Env:DB_USER, Env:DB_PASSWORD -ErrorAction SilentlyContinue
$env:COOKIE_SECURE = 'false'
$env:MAIL_ENABLED = 'false'
$env:PORT = '8080'
if (!$env:JWT_SECRET) {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
}
try {
    if ($BootstrapAdmin) {
        $env:ADMIN_EMAIL = Read-Host 'ADMIN email (only for an empty accounts table)'
        $securePassword = Read-Host 'ADMIN password (hidden, at least 8 characters)' -AsSecureString
        try { $env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password }
        finally { $securePassword.Dispose() }
        if ([string]::IsNullOrWhiteSpace($env:ADMIN_EMAIL) -or $env:ADMIN_PASSWORD.Length -lt 8) {
            throw 'ADMIN email is required and password must have at least 8 characters.'
        }
    } else {
        Remove-Item Env:ADMIN_EMAIL, Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue
    }
    Write-Host 'Starting localhost:8080 -> localhost:1433 / UTE_Burrito; sqlserver, demo=false, seed=false.'
    $log = Join-Path $repoRoot 'target/sqlserver-acceptance-startup.log'
    & $java "-Djava.library.path=$NativeAuthDirectory" -jar $jar '--uteb.demo=false' '--uteb.seed=false' '--logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR' 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Spring Boot exited with code $LASTEXITCODE" }
} finally {
    Remove-Item Env:ADMIN_EMAIL, Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
