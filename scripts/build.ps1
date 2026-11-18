<#
.SYNOPSIS
    Baut und prueft SimplePlots ohne automatische Versionsaenderung.
.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments)][string[]]$MavenArgumente = @()
)

. (Join-Path $PSScriptRoot 'tools.ps1')

$Argumente = @()
foreach ($Argument in $MavenArgumente) {
    switch -Regex ($Argument) {
        '^(nobump|no-bump|ci|--ci)$' { continue }
        '^(skiptests|skip-tests|notests|.*skipTests.*|.*maven.test.skip.*)$' {
            throw 'Der gepruefte Build fuehrt alle Tests aus. Fuer einen schnellen Entwicklungsbuild Maven direkt nutzen.'
        }
        default { $Argumente += $Argument }
    }
}

$JavaHome = Get-SimplePlotsJavaHome
$Python = Get-SimplePlotsPython
$Maven = Get-SimplePlotsMaven
$BisherigesJavaHome = $env:JAVA_HOME
Push-Location $SimplePlotsProjekt
try {
    $env:JAVA_HOME = $JavaHome
    Write-Host "JDK: $JavaHome"
    Write-Host "Maven: $Maven"
    Invoke-SimplePlotsPython $Python @('scripts/check-version.py')
    Invoke-SimplePlotsPython $Python @('scripts/check-docs.py')
    Invoke-SimplePlotsPython $Python @('-m', 'unittest', 'discover', '-s', 'scripts', '-p', 'test_*.py')
    & (Join-Path $PSScriptRoot 'test-tools.ps1')
    & $Maven -B -ntp -pl paper -am clean verify @Argumente
    if ($LASTEXITCODE -ne 0) { throw 'Maven-Build fehlgeschlagen.' }
    Invoke-SimplePlotsPython $Python @('scripts/check-version.py', '--jar')
    Invoke-SimplePlotsPython $Python @('scripts/prepare-release.py')
    Write-Host "Gepruefte JAR und SHA256SUMS.txt: $(Join-Path $SimplePlotsProjekt 'dist')"
} finally {
    $env:JAVA_HOME = $BisherigesJavaHome
    Pop-Location
}
