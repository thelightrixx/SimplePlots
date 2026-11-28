# Gemeinsame Werkzeuge für Build und lokalen Paper-Testserver (PowerShell 5.1+).
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$SimplePlotsProjekt = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))

function Get-SimplePlotsVersion {
    [xml]$Pom = [IO.File]::ReadAllText((Join-Path $SimplePlotsProjekt 'pom.xml'))
    return $Pom.project.version.Trim()
}

function Get-SimplePlotsJavaHome {
    $Kandidaten = @()
    if ($env:JAVA_HOME) { $Kandidaten += $env:JAVA_HOME }
    $Java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($Java) { $Kandidaten += Split-Path -Parent (Split-Path -Parent $Java.Source) }
    $Kandidaten += Join-Path $env:USERPROFILE '.jdks/temurin-25'
    foreach ($Kandidat in ($Kandidaten | Select-Object -Unique)) {
        $JavaExe = Join-Path $Kandidat 'bin/java.exe'
        $JavacExe = Join-Path $Kandidat 'bin/javac.exe'
        if (!(Test-Path -LiteralPath $JavaExe -PathType Leaf) -or
            !(Test-Path -LiteralPath $JavacExe -PathType Leaf)) { continue }
        # Java schreibt die Versionsdaten auf stderr; PowerShell 5.1 erzeugt daraus ErrorRecords.
        $BisherigeFehlerbehandlung = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $Ausgabe = & $JavaExe -XshowSettings:properties -version 2>&1 | Out-String
        } finally {
            $ErrorActionPreference = $BisherigeFehlerbehandlung
        }
        if ($LASTEXITCODE -eq 0 -and $Ausgabe -match 'java.specification.version\s*=\s*(\d+)') {
            if ([int]$Matches[1] -ge 25) { return [IO.Path]::GetFullPath($Kandidat) }
        }
    }
    throw 'JDK 25 oder neuer fehlt. JAVA_HOME auf ein JDK setzen (nicht auf eine JRE).'
}

function Get-SimplePlotsPython {
    foreach ($Name in @('python', 'python3')) {
        $Python = Get-Command $Name -ErrorAction SilentlyContinue
        if (!$Python) { continue }
        & $Python.Source -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)' 2>$null
        if ($LASTEXITCODE -eq 0) { return $Python.Source }
    }
    throw 'Python 3.10 oder neuer fehlt. Python installieren und zum PATH hinzufuegen.'
}

function Get-SimplePlotsHash {
    param([string]$Pfad, [ValidateSet('SHA256', 'SHA512')][string]$Algorithmus)
    $Hash = if ($Algorithmus -eq 'SHA256') { [Security.Cryptography.SHA256]::Create() } else { [Security.Cryptography.SHA512]::Create() }
    $Stream = $null
    try {
        $Stream = [IO.File]::OpenRead($Pfad)
        return [BitConverter]::ToString($Hash.ComputeHash($Stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        if ($Stream) { $Stream.Dispose() }
        $Hash.Dispose()
    }
}

function Receive-SimplePlotsFile {
    param(
        [Parameter(Mandatory)][uri]$Uri,
        [Parameter(Mandatory)][string]$Ziel,
        [Parameter(Mandatory)][ValidateSet('SHA256', 'SHA512')][string]$Algorithmus,
        [Parameter(Mandatory)][string]$Prüfsumme
    )
    $Länge = if ($Algorithmus -eq 'SHA256') { 64 } else { 128 }
    if ($Uri.Scheme -ne 'https' -or $Prüfsumme -notmatch "^[a-fA-F0-9]{$Länge}$") {
        throw 'Download erfordert HTTPS und eine gueltige Pruefsumme.'
    }
    if (Test-Path -LiteralPath $Ziel -PathType Leaf) {
        if ((Get-SimplePlotsHash $Ziel $Algorithmus) -ieq $Prüfsumme) { return }
        throw "Prüfsumme der vorhandenen Datei stimmt nicht: $Ziel. Datei pruefen und manuell entfernen."
    }
    $Ordner = Split-Path -Parent $Ziel
    New-Item -ItemType Directory -Path $Ordner -Force | Out-Null
    $Temp = Join-Path $Ordner ([IO.Path]::GetRandomFileName() + '.tmp')
    try {
        Invoke-WebRequest -Uri $Uri -OutFile $Temp -UseBasicParsing -TimeoutSec 300 `
            -UserAgent 'SimplePlots-dev (https://github.com/thelightrixx/SimplePlots)'
        if ((Get-SimplePlotsHash $Temp $Algorithmus) -ine $Prüfsumme) {
            throw "Prüfsumme für $Uri stimmt nicht. Download wird verworfen."
        }
        Move-Item -LiteralPath $Temp -Destination $Ziel
    } finally {
        if (Test-Path -LiteralPath $Temp) { Remove-Item -LiteralPath $Temp -Force }
    }
}

function Get-SimplePlotsMaven {
    $Vorgabe = $env:SIMPLEPLOTS_MVN
    if (!$Vorgabe) { $Vorgabe = $env:PLOTSYSTEM_MVN }
    if ($Vorgabe) {
        if (!(Test-Path -LiteralPath $Vorgabe -PathType Leaf)) {
            throw "Maven aus SIMPLEPLOTS_MVN/PLOTSYSTEM_MVN fehlt: $Vorgabe"
        }
        return (Resolve-Path -LiteralPath $Vorgabe).Path
    }
    $Maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($Maven) { return $Maven.Source }
    $Tools = Join-Path $SimplePlotsProjekt '.tools'
    $Version = '3.9.11'
    $Maven = Join-Path $Tools "apache-maven-$Version/bin/mvn.cmd"
    if (!(Test-Path -LiteralPath $Maven -PathType Leaf)) {
        $Zip = Join-Path $Tools "apache-maven-$Version-bin.zip"
        # Offizielle SHA-512: archive.apache.org/dist/maven/maven-3/3.9.11/binaries/.
        $Sha = '03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076'
        Write-Host "Lade Apache Maven $Version nach .tools (SHA-512 geprueft)."
        Receive-SimplePlotsFile -Uri "https://archive.apache.org/dist/maven/maven-3/$Version/binaries/apache-maven-$Version-bin.zip" `
            -Ziel $Zip -Algorithmus SHA512 -Pruefsumme $Sha
        Expand-Archive -LiteralPath $Zip -DestinationPath $Tools -Force
    }
    return $Maven
}

function Invoke-SimplePlotsPython {
    param([Parameter(Mandatory)][string]$Python, [Parameter(Mandatory)][string[]]$Argumente)
    & $Python @Argumente
    if ($LASTEXITCODE -ne 0) { throw "Python-Pruefung fehlgeschlagen: $($Argumente -join ' ')" }
}
