# Verhaltenspruefungen fuer die gemeinsam verwendeten Build-Werkzeuge, ohne Netzwerk.
. (Join-Path $PSScriptRoot 'tools.ps1')
$Ordner = Join-Path $SimplePlotsProjekt ('.tools/test-tools-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Ordner -Force | Out-Null
$Datei = Join-Path $Ordner 'cache.bin'

function Assert-Fehler {
    param([scriptblock]$Aktion, [string]$Erwartet)
    try { & $Aktion } catch {
        if ($_.Exception.Message -like "*$Erwartet*") { return }
        throw "Falscher Fehler: $($_.Exception.Message)"
    }
    throw "Erwarteter Fehler fehlt: $Erwartet"
}

try {
    Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' | ForEach-Object {
        $Tokens = $null
        $Fehler = $null
        [Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$Tokens, [ref]$Fehler) | Out-Null
        if ($Fehler.Count -gt 0) { throw "PowerShell-Syntax ungueltig: $($_.Name): $Fehler" }
    }
    [IO.File]::WriteAllText($Datei, 'SimplePlots cache')
    $Sha = Get-SimplePlotsHash $Datei SHA256
    Receive-SimplePlotsFile -Uri 'https://example.invalid/cache' -Ziel $Datei -Algorithmus SHA256 -Pruefsumme $Sha
    Assert-Fehler { Receive-SimplePlotsFile -Uri 'http://example.invalid/cache' -Ziel $Datei -Algorithmus SHA256 -Pruefsumme $Sha } 'HTTPS'
    Assert-Fehler { Receive-SimplePlotsFile -Uri 'https://example.invalid/cache' -Ziel $Datei -Algorithmus SHA256 -Pruefsumme 'kaputt' } 'Pruefsumme'
    [IO.File]::WriteAllText($Datei, 'beschaedigter Cache')
    Assert-Fehler { Receive-SimplePlotsFile -Uri 'https://example.invalid/cache' -Ziel $Datei -Algorithmus SHA256 -Pruefsumme $Sha } 'vorhandenen Datei'
    if ([IO.File]::ReadAllText($Datei) -ne 'beschaedigter Cache') { throw 'Beschaedigter Cache wurde veraendert.' }
    Push-Location $Ordner
    try {
        if ((Get-SimplePlotsVersion) -notmatch '^v[0-9]') { throw 'Projektwurzel haengt vom Arbeitsverzeichnis ab.' }
    } finally { Pop-Location }
    Get-SimplePlotsJavaHome | Out-Null
    Get-SimplePlotsPython | Out-Null
    Write-Host 'PowerShell-Werkzeuge: Syntax, Cache, Pruefsummen, Projektpfad und Laufzeiten erfolgreich geprueft.'
} finally {
    Remove-Item -LiteralPath $Datei -Force -ErrorAction SilentlyContinue
    # Nur die oben erzeugte, nicht rekursiv geloeschte temporaere Datei/Verzeichnis.
    Remove-Item -LiteralPath $Ordner -ErrorAction SilentlyContinue
}
