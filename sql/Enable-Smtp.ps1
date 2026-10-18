# Dot-source this file in the SAME PowerShell terminal that will start Maven.
param(
    [string]$SmtpHostName,
    [ValidateRange(1,65535)][int]$SmtpPortNumber = 587
)
if ([string]::IsNullOrWhiteSpace($SmtpHostName)) {
    $SmtpHostName = Read-Host 'SMTP host (from your email provider)'
}
if ([string]::IsNullOrWhiteSpace($SmtpHostName)) { throw 'SMTP host is required.' }
$smtpLoginName = Read-Host 'SMTP user (usually the full email address)'
if ([string]::IsNullOrWhiteSpace($smtpLoginName)) { throw 'SMTP user is required.' }
$smtpSecretValue = Read-Host 'SMTP password / NEW App Password (hidden)' -AsSecureString
if ($smtpSecretValue.Length -eq 0) { throw 'SMTP password is required.' }
$env:SMTP_HOST = $SmtpHostName
$env:SMTP_PORT = [string]$SmtpPortNumber
$env:SMTP_USER = $smtpLoginName
$env:SMTP_PASSWORD = [System.Net.NetworkCredential]::new('', $smtpSecretValue).Password
$env:MAIL_FROM = $smtpLoginName
$env:MAIL_ENABLED = 'true'
Remove-Variable smtpSecretValue
Write-Host 'SMTP enabled in this terminal. Keep your SQL variables, then run .\mvnw.cmd spring-boot:run here.'
