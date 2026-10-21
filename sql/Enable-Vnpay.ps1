# Dot-source in the SAME PowerShell session that already contains SQL settings.
param([string]$PublicBaseUrl)
if ([string]::IsNullOrWhiteSpace($PublicBaseUrl)) { $PublicBaseUrl = Read-Host 'Current HTTPS website/tunnel URL' }
$vnpayBaseUri = $null
if (![Uri]::TryCreate($PublicBaseUrl, [UriKind]::Absolute, [ref]$vnpayBaseUri) -or $vnpayBaseUri.Scheme -ne 'https' -or $vnpayBaseUri.Query -or $vnpayBaseUri.Fragment -or $vnpayBaseUri.UserInfo -or $vnpayBaseUri.AbsolutePath -ne '/') {
    throw 'Use the HTTPS origin of your current tunnel, without a path, query or fragment.'
}
$vnpayMerchantCode = (Read-Host 'VNPay vnp_TmnCode').Trim()
if ($vnpayMerchantCode -notmatch '^[A-Za-z0-9]{8}$') { throw 'TmnCode must contain 8 letters/digits.' }
$vnpayHashValue = Read-Host 'VNPay vnp_HashSecret (hidden)' -AsSecureString
if ($vnpayHashValue.Length -eq 0) { throw 'HashSecret is required.' }
$env:VNPAY_TMN_CODE = $vnpayMerchantCode
$env:VNPAY_HASH_SECRET = [System.Net.NetworkCredential]::new('', $vnpayHashValue).Password
$env:BASE_URL = $vnpayBaseUri.GetLeftPart([UriPartial]::Authority)
$env:COOKIE_SECURE = 'true'
Remove-Variable vnpayHashValue
Write-Host 'VNPay Sandbox configured in this PowerShell session. Secret was not saved to a file.'
Write-Host ('Return URL: ' + $env:BASE_URL + '/payments/vnpay/return')
Write-Host ('IPN URL: ' + $env:BASE_URL + '/payments/vnpay/ipn')
