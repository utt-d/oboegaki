param(
    [string]$SigningDirectory = 'C:\Users\utt55\Desktop\oboegaki\release-signing'
)

$ErrorActionPreference = 'Stop'
$keystorePath = Join-Path $SigningDirectory 'oboegaki-release.jks'
$secretPath = Join-Path $SigningDirectory 'release-password.dpapi'

if (-not (Test-Path -LiteralPath $keystorePath) -or -not (Test-Path -LiteralPath $secretPath)) {
    throw 'Release signing key is missing. See PUBLISHING.md.'
}

$securePassword = Get-Content -LiteralPath $secretPath | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:OBOEGAKI_RELEASE_KEYSTORE = (Resolve-Path -LiteralPath $keystorePath).Path
    $env:OBOEGAKI_RELEASE_STORE_PASSWORD = $plainPassword
    $env:OBOEGAKI_RELEASE_KEY_ALIAS = 'oboegaki'
    $env:OBOEGAKI_RELEASE_KEY_PASSWORD = $plainPassword
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    & (Join-Path $PSScriptRoot '..\gradlew.bat') :androidApp:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Release build failed with exit code $LASTEXITCODE" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $plainPassword = $null
    Remove-Item Env:OBOEGAKI_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:OBOEGAKI_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
