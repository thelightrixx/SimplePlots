<#
.SYNOPSIS
    Bereitet einen lokalen Paper-Testserver vor und startet ihn auf 127.0.0.1.
.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-test.ps1 -PrepareOnly
.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-test.ps1 -NoBuild -AcceptEula
#>
[CmdletBinding()]
param(
    [ValidatePattern('^[1-9][0-9]*[MG]$')][string]$Ram = '2G',
    [ValidateRange(1024, 65535)][int]$ServerPort = 25585,
    [switch]$OfflineMode,
    [switch]$NoBuild,
    [switch]$PrepareOnly,
    [switch]$AcceptEula
)

. (Join-Path $PSScriptRoot 'tools.ps1')
$JavaHome = Get-SimplePlotsJavaHome
$Python = Get-SimplePlotsPython
if (!$NoBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
Push-Location $SimplePlotsProjekt
try {
    Invoke-SimplePlotsPython $Python @('scripts/check-version.py', '--jar')
    [xml]$Pom = [IO.File]::ReadAllText((Join-Path $SimplePlotsProjekt 'pom.xml'))
    if ($Pom.project.properties.'paper.version' -notmatch '^(?<mc>[0-9.]+)\.build\.(?<build>[0-9]+)-stable$') {
        throw 'paper.version muss einen festen stabilen Paper-Build enthalten.'
    }
    $McVersion = $Matches.mc
    $Build = $Matches.build
    $ServerOrdner = Join-Path $SimplePlotsProjekt 'test-server'
    $Plugins = Join-Path $ServerOrdner 'plugins'
    New-Item -ItemType Directory -Path $Plugins -Force | Out-Null
    $PaperJar = Join-Path $ServerOrdner "paper-$McVersion-$Build.jar"
    $Manifest = Join-Path $ServerOrdner "paper-$McVersion-$Build.json"
    if (Test-Path -LiteralPath $Manifest -PathType Leaf) {
        $Metadaten = [IO.File]::ReadAllText($Manifest) | ConvertFrom-Json
    } else {
        $Metadaten = Invoke-RestMethod -Uri "https://fill.papermc.io/v3/projects/paper/versions/$McVersion/builds/$Build" `
            -UserAgent 'SimplePlots-dev (https://github.com/thelightrixx/SimplePlots)' -TimeoutSec 30
    }
    if ($Metadaten.id -ne [int]$Build -or $Metadaten.channel -ne 'STABLE') {
        throw 'Die Paper-Metadaten passen nicht zum festgelegten stabilen Build.'
    }
    $Download = $Metadaten.downloads.'server:default'
    Receive-SimplePlotsFile -Uri $Download.url -Ziel $PaperJar -Algorithmus SHA256 -Pruefsumme $Download.checksums.sha256
    [IO.File]::WriteAllText($Manifest, ($Metadaten | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))

    $FremdeJars = @(Get-ChildItem -LiteralPath $Plugins -File | Where-Object {
        ($_.Name -like 'SimplePlots*.jar' -and $_.Name -ne 'SimplePlots.jar') -or $_.Name -like 'PlotSystem*.jar'
    })
    if ($FremdeJars.Count -gt 0) {
        throw 'Weitere SimplePlots-/PlotSystem-JARs in test-server/plugins gefunden. Doppelte Plugins zuerst manuell entfernen.'
    }
    $Version = Get-SimplePlotsVersion
    Copy-Item -LiteralPath (Join-Path $SimplePlotsProjekt "paper/target/SimplePlots-$Version-paper.jar") `
        -Destination (Join-Path $Plugins 'SimplePlots.jar') -Force
    $Properties = Join-Path $ServerOrdner 'server.properties'
    if (!(Test-Path -LiteralPath $Properties)) {
        $OnlineMode = if ($OfflineMode) { 'false' } else { 'true' }
        $Zeilen = @(
            'motd=SimplePlots DEV', 'server-ip=127.0.0.1', "server-port=$ServerPort",
            "online-mode=$OnlineMode", 'spawn-protection=0', 'max-players=5',
            'view-distance=4', 'simulation-distance=4', 'gamemode=survival',
            'enable-rcon=false', 'enable-query=false'
        )
        [IO.File]::WriteAllLines($Properties, $Zeilen, [Text.UTF8Encoding]::new($false))
    }
    $Eula = Join-Path $ServerOrdner 'eula.txt'
    if ($AcceptEula) {
        [IO.File]::WriteAllText($Eula, "eula=true`n", [Text.UTF8Encoding]::new($false))
    } elseif (!(Test-Path -LiteralPath $Eula)) {
        [IO.File]::WriteAllText($Eula, "eula=false`n", [Text.UTF8Encoding]::new($false))
    }
    Write-Host "Paper $McVersion Build $Build und SimplePlots $Version vorbereitet: $ServerOrdner"
    Write-Host 'Bestehende server.properties bleiben erhalten; Port und Online-Modus dort pruefen.'
    if ($PrepareOnly) { return }
    if ([IO.File]::ReadAllText($Eula) -notmatch '(?m)^\s*eula\s*=\s*true\s*$') {
        throw 'Minecraft-EULA noch nicht akzeptiert. https://aka.ms/MinecraftEULA lesen und danach -AcceptEula verwenden.'
    }
    Push-Location $ServerOrdner
    try {
        & (Join-Path $JavaHome 'bin/java.exe') "-Xms$Ram" "-Xmx$Ram" -jar $PaperJar --nogui
        if ($LASTEXITCODE -ne 0) { throw "Paper wurde mit Fehlercode $LASTEXITCODE beendet." }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}
