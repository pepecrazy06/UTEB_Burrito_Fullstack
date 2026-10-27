[CmdletBinding()]
param(
    [string]$Server = 'localhost',
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_]+$')][string]$Database,
    [ValidateSet('windows','sql')][string]$Authentication = 'windows',
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Data
$builder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source'] = $Server
$builder['Initial Catalog'] = $Database
$builder['Encrypt'] = $true
$builder['TrustServerCertificate'] = $true
$builder['Connect Timeout'] = 5
if ($Authentication -eq 'windows') { $builder['Integrated Security'] = $true }
else {
    if (!$env:DB_USER -or !$env:DB_PASSWORD) { throw 'Set DB_USER and DB_PASSWORD privately first.' }
    $builder['User ID'] = $env:DB_USER
    $builder['Password'] = $env:DB_PASSWORD
}
$connection = [System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
$connection.Open()
function Read-Scalar([string]$Sql) {
    $command = $connection.CreateCommand()
    try { $command.CommandText = $Sql; return $command.ExecuteScalar() }
    finally { $command.Dispose() }
}
try {
    $tableCount = [int](Read-Scalar 'SELECT COUNT(*) FROM sys.tables')
    $historyExists = [int](Read-Scalar "SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID(N'dbo.schema_migrations')")
    $collation = [string](Read-Scalar "SELECT collation_name FROM sys.databases WHERE name=DB_NAME()")
    Write-Output "Target: $Server / $Database; tables=$tableCount; collation=$collation"
    $files = @()
    if ($tableCount -eq 0) {
        if ($collation -notmatch '_UTF8$') {
            throw 'Empty database needs a UTF8 collation before V1 varchar columns are created. Review and approve ALTER DATABASE separately.'
        }
        $files = @('V1__schema.sql','V2__constraints.sql','V3__catalog_seed.sql','V4__account_birth_date.sql','V5__vnpay_payment_attempts.sql','V8__order_cancellations.sql','V9__store_coordinates.sql')
    } elseif ($historyExists -eq 0) {
        throw 'Existing tables without migration history: stop for a manual schema audit. Never recreate them.'
    } else {
        $historyCommand = $connection.CreateCommand()
        $historyCommand.CommandText = 'SELECT version_no FROM dbo.schema_migrations ORDER BY version_no'
        $reader = $historyCommand.ExecuteReader()
        $versions = @()
        try { while ($reader.Read()) { $versions += $reader.GetInt32(0) } }
        finally { $reader.Dispose(); $historyCommand.Dispose() }
        Write-Output "Applied versions: $($versions -join ', ')"
        if (@(1,2,3 | Where-Object { $_ -notin $versions }).Count -gt 0) {
            throw 'Incomplete V1-V3 history: manual review required; no old scripts will be replayed.'
        }
        if (4 -notin $versions) { $files += 'V4__account_birth_date.sql' }
        if (5 -notin $versions) { $files += 'V5__vnpay_payment_attempts.sql' }
        if (8 -notin $versions) { $files += 'V8__order_cancellations.sql' }
        else {
            if ([int](Read-Scalar "SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no=8 AND name=N'V8__order_cancellations'") -ne 1) {
                throw 'V8 belongs to a different migration. Stop for a manual audit.'
            }
            if ([int](Read-Scalar "SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID(N'dbo.order_cancellations')") -ne 1) {
                throw 'V8 is recorded but order_cancellations is missing. Restore/audit; do not recreate data.'
            }
        }
    }
    if ($tableCount -ne 0) {
        if (9 -notin $versions) { $files += 'V9__store_coordinates.sql' }
        elseif ([int](Read-Scalar "SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no=9 AND name=N'V9__store_coordinates'") -ne 1) {
            throw 'V9 belongs to a different migration. Stop for a manual audit.'
        }
    }
    if ($files.Count -eq 0) { Write-Output 'No pending migrations for this branch (V1-V5, V8 and V9).'; return }
    Write-Output "Pending: $($files -join ', ')"
    if (!$Apply) { Write-Output 'Plan only. Run with -Apply only after approval.'; return }
    $transaction = $connection.BeginTransaction()
    try {
        foreach ($file in $files) {
            $source = Get-Content -LiteralPath (Join-Path $PSScriptRoot $file) -Raw -Encoding utf8
            # Historical scripts select uteb. Keep their bytes in Git unchanged and
            # remove ONLY that known directive; this connection already selects -Database.
            $source = [regex]::Replace($source, '(?im)^\s*USE\s+\[?uteb\]?\s*;\s*$', '')
            if ($source -match '(?im)^\s*USE\s+') { throw "Unexpected database switch in $file" }
            foreach ($batch in [regex]::Split($source, '(?im)^\s*GO\s*$')) {
                if ([string]::IsNullOrWhiteSpace($batch)) { continue }
                $command = $connection.CreateCommand()
                try {
                    $command.Transaction = $transaction
                    $command.CommandTimeout = 120
                    $command.CommandText = "SET XACT_ABORT ON;`n$batch"
                    [void]$command.ExecuteNonQuery()
                } finally { $command.Dispose() }
            }
            Write-Output "Applied in transaction: $file"
        }
        $transaction.Commit()
        Write-Output 'Committed schema migrations. V3 contains sample catalog only, no demo accounts/orders.'
    } catch {
        try { $transaction.Rollback() } catch { }
        throw
    } finally { $transaction.Dispose() }
} finally { $connection.Dispose() }
