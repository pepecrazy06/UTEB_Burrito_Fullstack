[CmdletBinding()]
param([string]$Server='localhost')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$prefix='uteb_commerce_test_'+[guid]::NewGuid().ToString('N').Substring(0,12)+'_'
$created=@()
function Connect([string]$Database){
 $connection=[System.Data.SqlClient.SqlConnection]::new("Server=$Server;Database=$Database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connect Timeout=5")
 $connection.Open();return $connection
}
function Query($Connection,[string]$Sql){$cmd=$Connection.CreateCommand();try{$cmd.CommandText=$Sql;$cmd.CommandTimeout=120;return $cmd.ExecuteScalar()}finally{$cmd.Dispose()}}
function Script($Connection,[string]$File){
 $source=[IO.File]::ReadAllText((Join-Path $PSScriptRoot $File))
 $source=[regex]::Replace($source,'(?im)^\s*USE\s+\[?uteb\]?\s*;\s*$','')
 if($source -match '(?im)^\s*USE\s+'){throw 'Unexpected database switch.'}
 foreach($batch in [regex]::Split($source,'(?im)^\s*GO\s*$')){if(![string]::IsNullOrWhiteSpace($batch)){[void](Query $Connection $batch)}}
}
function Check([bool]$Ok,[string]$Message){if(!$Ok){throw $Message}}
$master=Connect 'master'
try{
 foreach($case in @('fresh','history','occupied')){
  $database=$prefix+$case
  if($database -notmatch '^uteb_commerce_test_[a-f0-9]{12}_(fresh|history|occupied)$'){throw 'Unsafe test database name.'}
  [void](Query $master "CREATE DATABASE [$database] COLLATE Latin1_General_100_CI_AS_SC_UTF8;");$created+=$database
  $connection=Connect $database
  try{
   if($case -eq 'fresh'){& "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply}
   else{
    foreach($file in @('V1__schema.sql','V2__constraints.sql','V3__catalog_seed.sql','V4__account_birth_date.sql','V5__vnpay_payment_attempts.sql','V8__order_cancellations.sql','V9__store_coordinates.sql','V10__integrated_dat_menu.sql')){Script $connection $file}
    if($case -eq 'occupied'){
     [void](Query $connection "INSERT dbo.schema_migrations(version_no,name) VALUES(11,N'Unrelated_V11');")
     $rejected=$false
     try{Script $connection 'V11__commerce_completion.sql'}catch{if($_.ToString() -like '*V11 belongs to another migration*'){$rejected=$true}else{throw}}
     Check $rejected 'Occupied V11 must be rejected.'
     Check ((Query $connection "SELECT COUNT(*) FROM sys.tables WHERE name='shipping_carriers'") -eq 0) 'Rejected upgrade must not change schema.'
     Write-Output 'PASS: occupied version rejected without schema changes.';continue
    }
    [void](Query $connection @'
INSERT dbo.accounts(active,otp_attempts,points,token_version,verified,version,email,name,roles)
VALUES(1,0,60,0,1,0,'history@example.test',N'Khách lịch sử','CUSTOMER');
DECLARE @user bigint=SCOPE_IDENTITY();
INSERT dbo.meal_orders(points_awarded,version,user_id,store_id,code,status,payment_status,payment_method,total)
VALUES(1,0,@user,1,'KEEP-COMMERCE-HISTORY','COMPLETED','PAID','COD',60000);
DECLARE @order bigint=SCOPE_IDENTITY();
INSERT dbo.refund_requests(version,order_id,user_id,store_id,reason,status,amount)
VALUES(0,@order,@user,1,'Historical refund request','REQUESTED',60000);
UPDATE dbo.products SET stock=17,price=123456 WHERE id=1;
'@)
    Script $connection 'V11__commerce_completion.sql'
    Check ((Query $connection "SELECT COUNT(*) FROM dbo.meal_orders WHERE code='KEEP-COMMERCE-HISTORY' AND total=60000 AND payment_status='PAID' AND shipping_discount=0 AND carrier_id IS NULL") -eq 1) 'Order/payment history must survive.'
    Check ((Query $connection "SELECT COUNT(*) FROM dbo.refund_requests WHERE reason='Historical refund request' AND kind='REFUND'") -eq 1) 'Legacy refund needs the compatible default.'
    Check ((Query $connection 'SELECT COUNT(*) FROM dbo.products WHERE id=1 AND stock=17 AND price=123456') -eq 1) 'Manual price/stock must survive.'
   }
   Check ((Query $connection "SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no=11 AND name=N'V11__commerce_completion'") -eq 1) 'V11 must be recorded exactly once.'
   Check ((Query $connection 'SELECT COUNT(*) FROM dbo.shipping_carriers') -eq 4) 'Each original store receives its existing internal delivery service.'
   Script $connection 'V11__commerce_completion.sql'
   Check ((Query $connection 'SELECT COUNT(*) FROM dbo.shipping_carriers') -eq 4) 'Replay must not duplicate carriers.'
   [void](Query $connection "INSERT dbo.shipping_carriers(version,store_id,name,fee,active) VALUES(0,1,N'Giao hàng kiểm thử',22000,1);")
   Check ((Query $connection "SELECT COUNT(*) FROM dbo.shipping_carriers WHERE name=N'Giao hàng kiểm thử'") -eq 1) 'Vietnamese carrier names must round-trip.'
   Write-Output "PASS: $case - additive schema, preserved history and repeat safe."
  }finally{$connection.Dispose()}
 }
}finally{
 [System.Data.SqlClient.SqlConnection]::ClearAllPools()
 foreach($database in $created){
  if(!$database.StartsWith($prefix) -or $database -notmatch '^uteb_commerce_test_[a-f0-9]{12}_(fresh|history|occupied)$'){throw 'Unsafe cleanup target.'}
  [void](Query $master "ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$database];")
 }
 $master.Dispose()
}
