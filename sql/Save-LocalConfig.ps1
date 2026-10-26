[CmdletBinding()]
param(
    [ValidateSet('all','sql','cloudinary')][string]$Section='all',
    [ValidatePattern('^[A-Za-z0-9_]+$')][string]$Database='uteb',
    [string]$SqlServer='localhost:1433',
    [string]$SqlLogin='sa',
    [string]$ConfigPath
)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
if(!$ConfigPath){$ConfigPath=Join-Path $repoRoot '.env.local.properties'}
$ConfigPath=[System.IO.Path]::GetFullPath($ConfigPath)
$lines=@()
if(Test-Path -LiteralPath $ConfigPath){$lines=@([System.IO.File]::ReadAllLines($ConfigPath))}
$updates=[ordered]@{}
function Current-Value([string]$Name){return [Environment]::GetEnvironmentVariable($Name,'Process')}
function Has-SavedValue([string]$Name){
    $pattern='^\s*'+[regex]::Escape($Name)+'\s*[=:]\s*\S'
    return @($lines | Where-Object {$_ -match $pattern}).Count -gt 0
}
function Private-Value([string]$Name,[string]$Prompt){
    $value=Current-Value $Name
    if([string]::IsNullOrWhiteSpace($value)){
        $secureValue=Read-Host $Prompt -AsSecureString
        try{$value=[System.Net.NetworkCredential]::new('', $secureValue).Password}
        finally{$secureValue.Dispose()}
    }
    if([string]::IsNullOrWhiteSpace($value)){throw "$Name is required; nothing has been saved."}
    return $value
}
function Property-Value([string]$Value){
    # Java .properties escaping keeps passwords with spaces, backslashes and
    # Unicode intact. Values are data, never PowerShell statements.
    $encoded=[System.Text.StringBuilder]::new()
    foreach($character in $Value.ToCharArray()){
        $code=[int]$character
        if($code -lt 32 -or $code -gt 126){[void]$encoded.Append(('\u{0:x4}' -f $code))}
        elseif('\ :=#!'.Contains([string]$character)){[void]$encoded.Append('\');[void]$encoded.Append($character)}
        else{[void]$encoded.Append($character)}
    }
    return $encoded.ToString()
}
if($Section -in @('all','sql')){
    $updates['DB_AUTH']='sql'
    $updates['DB_URL']=Current-Value 'DB_URL'
    if([string]::IsNullOrWhiteSpace($updates['DB_URL'])){
        $updates['DB_URL']="jdbc:sqlserver://${SqlServer};databaseName=$Database;encrypt=true;trustServerCertificate=true"
    }
    if(!$updates['DB_URL'].StartsWith('jdbc:sqlserver://') -or $updates['DB_URL'] -match '(?i);\s*integratedSecurity\s*=\s*true'){
        throw 'This helper saves SQL login authentication. Use a SQL Server URL without integratedSecurity=true.'
    }
    $updates['DB_USER']=Current-Value 'DB_USER'
    if([string]::IsNullOrWhiteSpace($updates['DB_USER'])){$updates['DB_USER']=$SqlLogin}
    if([string]::IsNullOrWhiteSpace($updates['DB_USER'])){throw 'SQL login is required.'}
    $updates['DB_PASSWORD']=Private-Value 'DB_PASSWORD' 'SQL Server password (hidden)'
}
if($Section -in @('all','cloudinary')){
    $cloud=Current-Value 'CLOUDINARY_CLOUD'
    if([string]::IsNullOrWhiteSpace($cloud)){$cloud=Read-Host 'Cloudinary Cloud name'}
    $cloud=$cloud.Trim()
    if($cloud -notmatch '^[A-Za-z0-9_-]+$'){throw 'Enter the Cloud name, not an account name or URL.'}
    $updates['CLOUDINARY_CLOUD']=$cloud
    $updates['CLOUDINARY_KEY']=(Private-Value 'CLOUDINARY_KEY' 'Cloudinary API Key (hidden)').Trim()
    if($updates['CLOUDINARY_KEY'] -notmatch '^\d+$'){throw 'Cloudinary API Key must contain digits only.'}
    $updates['CLOUDINARY_SECRET']=Private-Value 'CLOUDINARY_SECRET' 'Cloudinary API Secret (hidden)'
}
# Keep the saved JWT secret stable across restarts and partial updates.
if(!(Has-SavedValue 'JWT_SECRET')){
    $jwt=Current-Value 'JWT_SECRET'
    if([string]::IsNullOrWhiteSpace($jwt)){
        $bytes=New-Object byte[] 48
        $random=[System.Security.Cryptography.RandomNumberGenerator]::Create()
        try{$random.GetBytes($bytes)}finally{$random.Dispose()}
        $jwt=[Convert]::ToBase64String($bytes)
    }
    if($jwt.Length -lt 32){throw 'JWT_SECRET must have at least 32 characters.'}
    $updates['JWT_SECRET']=$jwt
}
# Also retain integrations already configured in the terminal where this helper
# is run, so restarting Maven does not silently disable those integrations.
foreach($key in @('PORT','BASE_URL','COOKIE_SECURE','MAIL_ENABLED','SMTP_HOST','SMTP_PORT','SMTP_USER','SMTP_PASSWORD','MAIL_FROM',
        'VNPAY_TMN_CODE','VNPAY_HASH_SECRET','MOMO_PARTNER_CODE','MOMO_ACCESS_KEY','MOMO_SECRET_KEY')){
    $value=Current-Value $key
    if(![string]::IsNullOrWhiteSpace($value)){$updates[$key]=$value}
}
foreach($entry in @(@('BASE_URL','http://localhost:8080'),@('PORT','8080'))){
    if(!$updates.Contains($entry[0]) -and !(Has-SavedValue $entry[0])){$updates[$entry[0]]=$entry[1]}
}
if(!$updates.Contains('COOKIE_SECURE') -and !(Has-SavedValue 'COOKIE_SECURE')){
    $effectiveBase=[string]$updates['BASE_URL']
    if(!$effectiveBase){
        $baseLine=@($lines | Where-Object {$_ -match '^\s*BASE_URL\s*[=:]'} | Select-Object -Last 1)
        if($baseLine.Count){$effectiveBase=$baseLine[0] -replace '^\s*BASE_URL\s*[=:]\s*',''}
    }
    $updates['COOKIE_SECURE']=($effectiveBase.StartsWith('https:') -or $effectiveBase.StartsWith('https\:')).ToString().ToLowerInvariant()
}
$result=[System.Collections.Generic.List[string]]::new()
$written=[System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
if($lines.Count -eq 0){$result.Add('# Private machine configuration. Never commit or share this file.');$result.Add('')}
foreach($line in $lines){
    if($line -match '^\s*([A-Za-z0-9_.-]+)\s*[=:]'){
        $key=$Matches[1]
        if($updates.Contains($key)){
            if($written.Add($key)){$result.Add($key+'='+(Property-Value ([string]$updates[$key])))}
            continue
        }
    }
    $result.Add($line)
}
foreach($key in $updates.Keys){if($written.Add($key)){$result.Add($key+'='+(Property-Value ([string]$updates[$key])))}}
$directory=Split-Path $ConfigPath -Parent
if(!(Test-Path -LiteralPath $directory)){[void][System.IO.Directory]::CreateDirectory($directory)}
$temporaryPath=$ConfigPath+'.'+[guid]::NewGuid().ToString('N')+'.tmp'
try{
    [System.IO.File]::WriteAllLines($temporaryPath,$result.ToArray(),[System.Text.UTF8Encoding]::new($false))
    if(Test-Path -LiteralPath $ConfigPath){[System.IO.File]::Replace($temporaryPath,$ConfigPath,[NullString]::Value)}
    else{[System.IO.File]::Move($temporaryPath,$ConfigPath)}
}finally{if(Test-Path -LiteralPath $temporaryPath){Remove-Item -LiteralPath $temporaryPath}}
Write-Host 'Saved private local configuration. Credentials were not printed.'
Write-Host 'From the project directory, run: .\mvnw.cmd spring-boot:run'
Write-Host 'You can also run UtebApplication in IntelliJ with the project directory as its working directory.'
