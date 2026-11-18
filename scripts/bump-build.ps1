[CmdletBinding(DefaultParameterSetName = 'Setzen')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Setzen')]
    [ValidatePattern('(?-i)^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$')]
    [string]$Version,

    [Parameter(Mandatory, ParameterSetName = 'Erhoehen')]
    [ValidateSet('patch', 'minor')]
    [string]$Increment
)

$ErrorActionPreference = 'Stop'
$Projekt = Split-Path -Parent $PSScriptRoot
$Namensraum = 'http://maven.apache.org/POM/4.0.0'
$Dateien = foreach ($Relativ in @('pom.xml', 'common/pom.xml', 'paper/pom.xml')) {
    $Pfad = Join-Path $Projekt $Relativ
    $Inhalt = [IO.File]::ReadAllText($Pfad)
    $Dokument = New-Object Xml.XmlDocument
    $Dokument.PreserveWhitespace = $true
    $Dokument.LoadXml($Inhalt)
    $Ns = New-Object Xml.XmlNamespaceManager($Dokument.NameTable)
    $Ns.AddNamespace('m', $Namensraum)
    $XPath = if ($Relativ -eq 'pom.xml') { '/m:project/m:version' } else { '/m:project/m:parent/m:version' }
    $Knoten = $Dokument.SelectSingleNode($XPath, $Ns)
    if ($null -eq $Knoten) { throw "Version in $Relativ fehlt." }

    [pscustomobject]@{
        Pfad = $Pfad
        Original = [IO.File]::ReadAllBytes($Pfad)
        Inhalt = $Inhalt
        Version = $Knoten.InnerText.Trim()
    }
}

$Aktuell = $Dateien[0].Version
if ($Dateien.Version | Where-Object { $_ -cne $Aktuell }) {
    throw 'Die Maven-Versionen stimmen nicht ueberein.'
}

if ($PSCmdlet.ParameterSetName -eq 'Erhoehen') {
    if ($Aktuell -cnotmatch '^v(?<major>[0-9]+)\.(?<minor>[0-9]+)\.(?<patch>[0-9]+)$') {
        throw 'Increment gilt fuer stabile Versionen. Verwende -Version fuer einen gezielten Versionswechsel.'
    }
    $Major = [long]$Matches.major
    $Minor = [long]$Matches.minor
    $Patch = [long]$Matches.patch
    if ($Increment -eq 'minor') {
        $Minor++
        $Patch = 0
    } else {
        $Patch++
    }
    $Version = "v$Major.$Minor.$Patch"
}

if ($Version -ceq $Aktuell) {
    Write-Output "Version bleibt $Aktuell."
    return
}

$Muster = [regex]::new('<version>(\s*)' + [regex]::Escape($Aktuell) + '(\s*)</version>')
foreach ($Datei in $Dateien) {
    if ($Muster.Matches($Datei.Inhalt).Count -ne 1) {
        throw "Die bisherige Version ist in $($Datei.Pfad) nicht eindeutig."
    }
}

$Geaendert = @()
try {
    foreach ($Datei in $Dateien) {
        $Neu = $Muster.Replace($Datei.Inhalt, '<version>${1}' + $Version + '${2}</version>', 1)
        $MitBom = $Datei.Original.Length -ge 3 -and $Datei.Original[0] -eq 239 -and
            $Datei.Original[1] -eq 187 -and $Datei.Original[2] -eq 191
        $Geaendert += $Datei
        [IO.File]::WriteAllText($Datei.Pfad, $Neu, [Text.UTF8Encoding]::new($MitBom))
    }
} catch {
    foreach ($Datei in $Geaendert) {
        [IO.File]::WriteAllBytes($Datei.Pfad, $Datei.Original)
    }
    throw
}

Write-Output "Maven-Version: $Aktuell -> $Version"
Write-Output 'Jetzt README, Dokumentation und Changelog aktualisieren und den Paper-Build pruefen.'
