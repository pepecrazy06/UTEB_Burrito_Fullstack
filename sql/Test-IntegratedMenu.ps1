[CmdletBinding()]
param([string]$Server='localhost')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$prefix='uteb_menu_test_'+[guid]::NewGuid().ToString('N').Substring(0,12)+'_'
$created=@()
function Connect([string]$Database){
    $connection=[System.Data.SqlClient.SqlConnection]::new("Server=$Server;Database=$Database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connect Timeout=5")
    $connection.Open();return $connection
}
function Query($Connection,[string]$Sql){
    $command=$Connection.CreateCommand()
    try{$command.CommandText=$Sql;$command.CommandTimeout=120;return $command.ExecuteScalar()}finally{$command.Dispose()}
}
function Script($Connection,[string]$File){
    $source=[System.IO.File]::ReadAllText((Join-Path $PSScriptRoot $File))
    $source=[regex]::Replace($source,'(?im)^\s*USE\s+\[?uteb\]?\s*;\s*$','')
    if($source -match '(?im)^\s*USE\s+'){throw 'Unexpected database switch.'}
    foreach($batch in [regex]::Split($source,'(?im)^\s*GO\s*$')){if(![string]::IsNullOrWhiteSpace($batch)){[void](Query $Connection $batch)}}
}
function Check([bool]$Ok,[string]$Message){if(!$Ok){throw $Message}}
$master=Connect 'master'
try{
    foreach($case in @('fresh','cancel_v6','menu_v6_v7','custom','occupied')){
        $database=$prefix+$case
        if($database -notmatch '^uteb_menu_test_[a-f0-9]{12}_(fresh|cancel_v6|menu_v6_v7|custom|occupied)$'){throw 'Unsafe test database name.'}
        [void](Query $master "CREATE DATABASE [$database] COLLATE Latin1_General_100_CI_AS_SC_UTF8;")
        $created+=$database;$connection=Connect $database
        try{
            if($case -ne 'fresh'){
                foreach($file in @('V1__schema.sql','V2__constraints.sql','V3__catalog_seed.sql','V4__account_birth_date.sql','V5__vnpay_payment_attempts.sql')){Script $connection $file}
                if($case -eq 'cancel_v6'){Script $connection 'V6__order_cancellations.sql'}
                if($case -eq 'menu_v6_v7'){Script $connection 'V6__menu_images.sql';Script $connection 'V7__menu_research_catalog.sql'}
                if($case -eq 'custom'){
                    [void](Query $connection @'
UPDATE dbo.products SET price=123456,image='https://res.cloudinary.com/example/image/upload/custom.jpg',stock=17 WHERE id=1;
INSERT dbo.accounts(active,otp_attempts,points,token_version,verified,version,email,name,roles)
VALUES(1,0,0,0,1,0,'history@example.test','History test','CUSTOMER');
DECLARE @user bigint=SCOPE_IDENTITY();
INSERT dbo.meal_orders(points_awarded,version,user_id,store_id,code,status,payment_status,payment_method,total)
VALUES(0,0,@user,1,'KEEP-MENU-HISTORY','COMPLETED','PAID','COD',77777);
DECLARE @order bigint=SCOPE_IDENTITY();
INSERT dbo.order_lines(version,calories,quantity,unit_price,order_id,product_id,name,image,customization)
VALUES(0,500,1,77777,@order,1,'Historical item','/assets/food.png','Original recipe');
'@)
                }
                if($case -eq 'occupied'){
                    [void](Query $connection "INSERT dbo.schema_migrations(version_no,name) VALUES(10,N'V10__unrelated');")
                    $rejected=$false
                    try{& "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply}catch{if($_.ToString() -like '*V10 belongs to a different migration*'){$rejected=$true}else{throw}}
                    Check $rejected 'Unknown V10 must stop the helper.'
                    Check ((Query $connection 'SELECT COUNT(*) FROM dbo.products') -eq 48) 'Rejected plan must not modify the catalog.'
                    Write-Output 'PASS: occupied V10 rejected without data updates.'
                    continue
                }
            }
            & "$PSScriptRoot/Apply-Schema.ps1" -Server $Server -Database $database -Apply
            Check ((Query $connection 'SELECT COUNT(*) FROM dbo.products WHERE available=1') -eq 96) 'Expected 96 active products.'
            Check ((Query $connection 'SELECT COUNT(*) FROM (SELECT store_id FROM dbo.products WHERE available=1 GROUP BY store_id HAVING COUNT(*)=24) t') -eq 4) 'Each branch must expose 24 products.'
            Check ((Query $connection 'SELECT COUNT(*) FROM dbo.products') -eq 122) 'Original product IDs must be preserved.'
            Check ((Query $connection "SELECT COUNT(*) FROM sys.tables WHERE name='order_cancellations'") -eq 1) 'Cancellation table must survive.'
            Check ((Query $connection "SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no=8 AND name=N'V8__order_cancellations'") -eq 1) 'V8 must still identify cancellation schema.'
            Check ([Math]::Abs([double](Query $connection 'SELECT latitude FROM dbo.stores WHERE id=2')-10.7745167) -lt 0.0000001) 'Corrected store locations must survive.'
            $cutouts=[int](Query $connection "SELECT COUNT(*) FROM dbo.products WHERE available=1 AND image LIKE '%-cutout.png'")
            if($case -eq 'custom'){
                Check ($cutouts -eq 96) 'The explicitly selected photo set must cover every active menu item.'
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.products WHERE id=1 AND price=123456 AND stock=17 AND image='/assets/menu/burrito-grilled-chicken-cutout.png'") -eq 1) 'Preserve price/stock while switching the selected menu photo.'
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.order_lines WHERE name='Historical item' AND unit_price=77777 AND image='/assets/food.png' AND customization='Original recipe'") -eq 1) 'Order snapshots must remain unchanged.'
                Check ((Query $connection "SELECT COUNT(*) FROM dbo.meal_orders WHERE code='KEEP-MENU-HISTORY' AND total=77777 AND status='COMPLETED' AND payment_status='PAID'") -eq 1) 'Paid order history must remain unchanged.'
            }else{Check ($cutouts -eq 96) 'Every active sample item must have the new cutout photo.'}
            $versionSum=Query $connection 'SELECT SUM(version) FROM dbo.products'
            Script $connection 'V10__integrated_dat_menu.sql'
            Check ((Query $connection 'SELECT SUM(version) FROM dbo.products') -eq $versionSum) 'Replay must not change products or add duplicates.'
            Write-Output "PASS: $case - 24 items per branch, preserved history/locations, repeat safe."
        }finally{$connection.Dispose()}
    }
}finally{
    [System.Data.SqlClient.SqlConnection]::ClearAllPools()
    foreach($database in $created){
        if(!$database.StartsWith($prefix) -or $database -notmatch '^uteb_menu_test_[a-f0-9]{12}_(fresh|cancel_v6|menu_v6_v7|custom|occupied)$'){throw 'Unsafe cleanup target.'}
        [void](Query $master "ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$database];")
    }
    $master.Dispose()
}
