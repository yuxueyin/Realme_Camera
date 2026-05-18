$path = "app\build.gradle.kts"
if (!(Test-Path $path)) {
    Write-Error "Cannot find $path. Run this script from the project root."
    exit 1
}

$content = Get-Content $path -Raw
$newContent = $content -replace 'versionName\s*=\s*"2\.3_enhance(\r?\n)', 'versionName = "2.3_enhance"$1'

if ($newContent -eq $content) {
    Write-Host "No broken versionName line matched. Checking existing line..."
    Select-String -Path $path -Pattern 'versionName' | ForEach-Object { Write-Host $_.Line }
    exit 0
}

Set-Content -Path $path -Value $newContent -Encoding UTF8
Write-Host "Fixed app/build.gradle.kts: versionName = \"2.3_enhance\""
