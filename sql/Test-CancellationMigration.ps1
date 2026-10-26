[CmdletBinding()]
param([string]$Server='localhost,1433',[string]$CatalogSqlDirectory='')
# Integration tests create and drop only databases bearing this invocation's random prefix.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$prefix='uteb_migration_test_'+[guid]::NewGuid().ToString('N').Substring(0,12)+'_'
$created=@()
function Connect([string]$Database){
    $builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $builder['Data Source']=$Server;$builder['Initial Catalog']=$Database
    $builder['Integrated Security']=$true;$builder['Encrypt']=$true;$builder['TrustServerCertificate']=$true
    $builder['Connect Timeout']=5
    $connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString);$connection.Open();return $connection
}
function Query([System.Data.SqlClient.SqlConnection]$Connection,[string]$Sql){
    $command=$Connection.CreateCommand()
    try{$command.CommandText=$Sql;$command.CommandTimeout=120;return $command.ExecuteScalar()}finally{$command.Dispose()}
}
function Script([System.Data.SqlClient.SqlConnection]$Connection,[string]$File){
    $source=Get-Content -LiteralPath $File -Raw -Encoding utf8
    $source=[regex]::Replace($source,'(?im)^\s*USE\s+\[?uteb\]?\s*;\s*$','')
    if($source -match '(?im)^\s*USE\s+'){throw 'Unexpected database switch in test script'}
    foreach($batch in [regex]::Split($source,'(?im)^\s*GO\s*$')){
        if(![string]::IsNullOrWhiteSpace($batch)){[void](Query $Connection $batch)}
    }
}
function Check([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
function Expect-Rejected([string]$Database,[int]$Number){
    $rejected=$false
    try{& "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $Database -Apply}
    catch {
        $errorCode=$_.Exception.GetBaseException()
        if($errorCode -is [System.Data.SqlClient.SqlException] -and $errorCode.Number -eq $Number){$rejected=$true}
        else {throw}
    }
    Check $rejected "Expected SQL error $Number for $Database"
}
$master=Connect 'master'
try {
    $cases=@('fresh','legacy','drift','missing','unknown')
    if($CatalogSqlDirectory){$cases+=@('catalog_first','cancellation_first')}
    :migrationCase foreach($case in $cases){
        $database=$prefix+$case
        [void](Query $master "CREATE DATABASE [$database] COLLATE Latin1_General_100_CI_AS_SC_UTF8;")
        $created+=$database
        $connection=Connect $database
        try {
            foreach($file in @('V1__schema.sql','V2__constraints.sql','V3__catalog_seed.sql','V4__account_birth_date.sql','V5__vnpay_payment_attempts.sql')){
                Script $connection (Join-Path $PSScriptRoot $file)
            }
            switch($case){
                'legacy' {
                    Script $connection "$PSScriptRoot/V6__order_cancellations.sql"
                    [void](Query $connection @'
INSERT dbo.accounts(active,otp_attempts,points,token_version,verified,version,email,name,roles)
VALUES(1,0,0,0,1,0,'legacy@example.test','Legacy customer','CUSTOMER');
DECLARE @customer bigint=SCOPE_IDENTITY(), @store bigint=(SELECT MIN(id) FROM dbo.stores);
INSERT dbo.meal_orders(points_awarded,version,user_id,store_id,code,status,payment_status,payment_method,total)
VALUES(0,0,@customer,@store,'LEGACY-KEEP','NEW','PAID','VNPAY',100000);
DECLARE @order bigint=SCOPE_IDENTITY();
INSERT dbo.order_cancellations(version,order_id,user_id,store_id,status,reason,amount,payment_method,payment_reference,created_at)
VALUES(0,@order,@customer,@store,'REQUESTED',N'Keep this existing cancellation.',100000,'VNPAY','LEGACY-REF',SYSDATETIME());
'@)
                }
                'drift' {
                    Script $connection "$PSScriptRoot/V6__order_cancellations.sql"
                    [void](Query $connection 'ALTER TABLE dbo.order_cancellations ALTER COLUMN reason nvarchar(1000) NOT NULL;')
                    Expect-Rejected $database 50015
                    Check ((Query $connection 'SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no=8') -eq 0) 'Rejected drift must not record V8'
                    Write-Output 'PASS: schema drift rejected without marking V8';continue migrationCase
                }
                'missing' {
                    Script $connection "$PSScriptRoot/V6__order_cancellations.sql"
                    [void](Query $connection 'DROP TABLE dbo.order_cancellations;')
                    Expect-Rejected $database 50013
                    Check ((Query $connection "SELECT COUNT(*) FROM sys.tables WHERE name='order_cancellations'") -eq 0) 'Do not recreate a lost legacy table'
                    Write-Output 'PASS: missing historical table requires restoration';continue migrationCase
                }
                'unknown' {
                    [void](Query $connection "INSERT dbo.schema_migrations(version_no,name) VALUES(8,N'V8__unrelated');")
                    $rejected=$false
                    try{& "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply}catch{
                        if($_.ToString() -like '*V8 belongs to a different migration*'){$rejected=$true}else{throw}
                    }
                    Check $rejected 'Unknown V8 must stop the helper'
                    Write-Output 'PASS: occupied V8 rejected';continue migrationCase
                }
                'catalog_first' {
                    Script $connection (Join-Path $CatalogSqlDirectory 'V6__menu_images.sql')
                    Script $connection (Join-Path $CatalogSqlDirectory 'V7__menu_research_catalog.sql')
                }
            }
            & "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply
            # Also execute the SQL directly a second time to verify idempotence, not just helper skipping.
            Script $connection "$PSScriptRoot/V8__order_cancellations.sql"
            if($case -eq 'cancellation_first'){
                Script $connection (Join-Path $CatalogSqlDirectory 'V6__menu_images.sql')
                Script $connection (Join-Path $CatalogSqlDirectory 'V7__menu_research_catalog.sql')
            }
            Check ((Query $connection "SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no=8 AND name=N'V8__order_cancellations'") -eq 1) 'Missing/duplicate cancellation V8 history'
            Check ((Query $connection "SELECT COUNT(*) FROM sys.tables WHERE name='order_cancellations'") -eq 1) 'Cancellation table missing'
            if($case -eq 'legacy'){
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.order_cancellations WHERE payment_reference='LEGACY-REF' AND reason=N'Keep this existing cancellation.' AND amount=100000") -eq 1) 'Legacy data changed'
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no=6 AND name=N'V6__order_cancellations'") -eq 1) 'Legacy V6 history changed'
            }
            if($case -in @('catalog_first','cancellation_first')){
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.products WHERE image=N'/assets/food.png'") -eq 0) 'Catalog image migration was skipped'
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (6,7,8)") -eq 3) 'Catalog/cancellation versions conflict'
            }
            Write-Output "PASS: $case, repeatable migration and preserved data"
        } finally {$connection.Dispose()}
    }
} finally {
    [System.Data.SqlClient.SqlConnection]::ClearAllPools()
    foreach($database in $created){
        if($database -notmatch ('^'+[regex]::Escape($prefix)+'(fresh|legacy|drift|missing|unknown|catalog_first|cancellation_first)$')){throw 'Unsafe cleanup target'}
        [void](Query $master "ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$database];")
    }
    $master.Dispose()
}
