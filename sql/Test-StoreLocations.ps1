[CmdletBinding()]
param([string]$Server='localhost')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$testPrefix='uteb_location_test_'+[guid]::NewGuid().ToString('N').Substring(0,12)+'_'
$testDatabases=[System.Collections.Generic.List[string]]::new()
function Connect([string]$Database){
    $connection=[System.Data.SqlClient.SqlConnection]::new("Data Source=$Server;Initial Catalog=$Database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connect Timeout=5")
    $connection.Open();return $connection
}
function Query($Connection,[string]$Sql){
    $command=$Connection.CreateCommand()
    try{$command.CommandText=$Sql;$command.CommandTimeout=60;return $command.ExecuteScalar()}finally{$command.Dispose()}
}
function Script($Connection,[string]$File){
    $source=[System.IO.File]::ReadAllText((Join-Path $PSScriptRoot $File))
    $source=[regex]::Replace($source,'(?im)^\s*USE\s+uteb\s*;\s*$','')
    if($source -match '(?im)^\s*USE\s+'){throw 'Unexpected database switch.'}
    foreach($batch in [regex]::Split($source,'(?im)^\s*GO\s*$')){if(![string]::IsNullOrWhiteSpace($batch)){[void](Query $Connection $batch)}}
}
function Check([bool]$Ok,[string]$Message){if(!$Ok){throw $Message}}
$master=Connect 'master'
try{
    foreach($case in @('fresh','customized','occupied')){
        $database=$testPrefix+$case
        if($database -notmatch '^uteb_location_test_[a-f0-9]{12}_(fresh|customized|occupied)$'){throw 'Unsafe test database name.'}
        [void](Query $master "CREATE DATABASE [$database] COLLATE Latin1_General_100_CI_AS_SC_UTF8;")
        $testDatabases.Add($database)
        $connection=Connect $database
        try{
            if($case -eq 'fresh'){
                & "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply
                Check ((Query $connection 'SELECT COUNT(*) FROM dbo.stores WHERE version=1') -eq 4) 'Fresh database must repair four sample locations.'
                Check ([Math]::Abs([double](Query $connection 'SELECT latitude FROM dbo.stores WHERE id=2')-10.7745167) -lt 0.0000001) 'District 1 marker is still misplaced.'
                Check ([Math]::Abs([double](Query $connection 'SELECT longitude FROM dbo.stores WHERE id=3')-106.7064464) -lt 0.0000001) 'Binh Thanh marker is still misplaced.'
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.stores WHERE id=4 AND address=N'88 Hoàng Diệu 2, phường Linh Chiểu, TP. Thủ Đức'") -eq 1) 'Hoang Dieu 2 address is inconsistent.'
                Check ((Query $connection 'SELECT COUNT(*) FROM dbo.products') -eq 122) 'Catalog products must be preserved.'
                $versionSum=Query $connection 'SELECT SUM(version) FROM dbo.stores'
                & "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply
                Script $connection 'V9__store_coordinates.sql'
                Check ((Query $connection 'SELECT SUM(version) FROM dbo.stores') -eq $versionSum) 'Replay must not move locations or increment versions.'
                [void](Query $connection 'UPDATE dbo.stores SET latitude=10.79,longitude=106.72,version=version+1 WHERE id=2;')
                Script $connection 'V9__store_coordinates.sql'
                Check ([Math]::Abs([double](Query $connection 'SELECT latitude FROM dbo.stores WHERE id=2')-10.79) -lt 0.0000001) 'Replay must preserve later user edits.'
                Write-Output 'PASS: fresh upgrade, repeat application, and later edits.'
            } else {
                foreach($file in @('V1__schema.sql','V2__constraints.sql','V3__catalog_seed.sql')){Script $connection $file}
                if($case -eq 'customized'){
                    [void](Query $connection "UPDATE dbo.stores SET latitude=10.84,longitude=106.78 WHERE id=1; UPDATE dbo.stores SET name=N'UTEB renamed' WHERE id=2; UPDATE dbo.stores SET address=N'Custom location' WHERE id=3;")
                    & "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply
                    Check ([Math]::Abs([double](Query $connection 'SELECT latitude FROM dbo.stores WHERE id=1')-10.84) -lt 0.0000001) 'Customized coordinates must survive.'
                    Check ((Query $connection "SELECT COUNT(*) FROM dbo.stores WHERE (id=2 AND name=N'UTEB renamed' AND version=0) OR (id=3 AND address=N'Custom location' AND version=0)") -eq 2) 'Renamed/relocated branches must survive.'
                    Check ([Math]::Abs([double](Query $connection 'SELECT latitude FROM dbo.stores WHERE id=4')-10.8567) -lt 0.0000001) 'Unchanged sample branch must still be repaired.'
                    Write-Output 'PASS: existing database preserves custom coordinates, names and addresses.'
                } else {
                    [void](Query $connection "INSERT dbo.schema_migrations(version_no,name) VALUES(9,N'V9__unrelated');")
                    $rejected=$false
                    try{& "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply}catch{if($_.ToString() -like '*V9 belongs to a different migration*'){$rejected=$true}else{throw}}
                    Check $rejected 'Unknown V9 must stop the migration helper.'
                    Check ((Query $connection 'SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (4,5,8)') -eq 0) 'Rejected plan must not apply earlier pending changes.'
                    Write-Output 'PASS: occupied migration version rejected without changes.'
                }
            }
        }finally{$connection.Dispose()}
    }
}finally{
    [System.Data.SqlClient.SqlConnection]::ClearAllPools()
    foreach($database in $testDatabases){
        if(!$database.StartsWith($testPrefix) -or $database -notmatch '^uteb_location_test_[a-f0-9]{12}_(fresh|customized|occupied)$'){throw 'Unsafe cleanup target.'}
        [void](Query $master "ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$database];")
    }
    $master.Dispose()
}
