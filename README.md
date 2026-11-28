# SimplePlots v0.11.0

SimplePlots ergänzt Paper um Plotwelten mit öffentlichen Wegen, Claims und Baurechten. Spieler bauen im Survival-Modus mit ihrem eigenen Inventar. Sie können Freunde einladen, Nachbarplots verbinden, Besuchspunkte setzen und an Bauwettbewerben teilnehmen.

## Einrichten

| Server | Java | JAR-Endung |
|---|---|---|
| Paper/Purpur 1.21.8 | 21 | `paper-1.21.jar` |
| Paper/Purpur 26.1 | 25 | `paper.jar` |

Installiere genau eine Variante. Eine Datenbank ist nicht nötig. EconomySystem ist optional und wird für kostenpflichtige Claims direkt angebunden. Folia wird nicht unterstützt.

Lege das Raster fest, bevor die Plotwelt erzeugt wird. Standardmäßig sind Plots 32 × 32 Blöcke groß, die Wege sieben Blöcke breit und drei Claims pro Spieler erlaubt. Die [Installationsanleitung](docs/index.html#/installation) führt durch die Einrichtung und den Wechsel von älteren Projektnamen.

## Benutzen

<!-- COMMAND-QUICKSTART -->
Mit `/simpleplots` oder `/simpleplots help [Seite]` siehst du die für deine Rechte verfügbaren Befehle. Die Verwaltung folgt `/simpleplots version`, `reload`, `diagnostics` und `update`. Ein Aufruf ohne Unterbefehl zeigt die Hilfe.
<!-- /COMMAND-QUICKSTART -->

### Im Spiel

Mit `/simpleplots world` betrittst du die Plotwelt, mit `/simpleplots auto` beanspruchst du den nächsten freien Plot. `/simpleplots help` zeigt die weiteren Befehle. Das [Spielerhandbuch](docs/index.html#/schnellstart) erklärt Baurechte, Besuchslisten und Menüs.

Sichere Plugin-Daten und Plotwelt gemeinsam. `plots.yml` und `plots-aenderungen.yml` bilden zusammen den gespeicherten Plotbestand; eine der beiden Dateien allein ist keine vollständige Sicherung.

Beim Update von v0.8.0-beta oder älter ist der Wechsel zu den neuen JAR-Dateinamen einmalig manuell nötig. Danach wählt der Updater die zur laufenden Plattform passende Variante. Näheres steht unter [Betrieb und Updates](docs/index.html#/betrieb).

### Wiederfinden und Grenzen ansehen

`/simpleplots bookmark add Mein Haus` merkt den aktuellen Plot. Mit `/simpleplots bookmark go Mein Haus` besuchst du ihn später wieder; `list` zeigt die Namen und `remove` entfernt einen Eintrag. Lesezeichen gelten je Plotwelt und bleiben in den Spielerdaten erhalten. Vor einem Besuch werden Welt, Besitzer, Zutritt und Zensur erneut geprüft.

`/simpleplots border` zeigt dir für 15 Sekunden die Außengrenze als grüne Partikel. Auch zusammengelegte Flächen und ihre Innenwege werden berücksichtigt. Die Anzeige verändert keine Blöcke und lädt keine Chunks. Dauer und Grenzen stehen unter `navigation` in `config/general.yml`.

Schreibänderungen werden vor dem Ersetzen gegen die Lesegrenze von 64 MiB je YAML-Datei geprüft. Eine zu große Änderung lässt den bisherigen Stand und seine Sicherung erhalten.

## Betrieb und Updates

<!-- COMMAND-MIGRATION -->
/simpleplots ist der gemeinsame Hauptbefehl; /plot und /p bleiben Aliase. Reload, Diagnose, Updates und Setup sind direkt darunter erreichbar. Bisherige Aufrufe mit admin und die Rechte simpleplots.admin.reload, .diagnostics und .update bleiben nutzbar und gewähren die neuen Einzelrechte. simpleplots.admin enthält keinen Plot-Bypass mehr; simpleplots.admin.bypass muss ausdrücklich vergeben werden. Ein leerer Aufruf zeigt die Hilfe; zur Plotwelt mit /simpleplots world reisen.
<!-- /COMMAND-MIGRATION -->

Mit `/simpleplots update check` prüfst du auf Updates; `status` zeigt den Stand. `/simpleplots update apply confirm` legt die geprüfte JAR der laufenden Plattformvariante für den nächsten normalen Serverstart in Papers Update-Ordner. Die Einstellungen stehen in `config/updates.yml`.

<!-- SHARED:readme-update-note -->
Lies vor einem Update den [Changelog](docs/index.html#/changelog). [Speicherung und Sicherungen](docs/index.html#/speicher) nennt die zusammengehörigen Daten; [Betrieb und Updates](docs/index.html#/betrieb) beschreibt Vorbereitung, Neustart und Wiederherstellung.
<!-- /SHARED:readme-update-note -->

## Nachlesen

Das [Online-Handbuch](https://thelightrixx.github.io/SimplePlots/) enthält dieselben Inhalte wie die lokale Datei.

<!-- SHARED:readme-help -->
Die lokale Datei [docs/index.html](docs/index.html) enthält Inhalte, Suche, Schriften und Beispiele. Sie funktioniert offline und lässt sich über den Browser drucken. Downloads und externe Referenzen brauchen eine Internetverbindung.

Direkt zu [Installation](docs/index.html#/installation), [Schnellstart](docs/index.html#/schnellstart), [Befehlen](docs/index.html#/befehle), [Berechtigungen](docs/index.html#/rechte), [Konfiguration](docs/index.html#/config-uebersicht), [Fehlerbehebung](docs/index.html#/fehlerbehebung) und [Entwicklung](docs/index.html#/projektstandard).
<!-- /SHARED:readme-help -->

## Download und eigener Build

Im [Release v0.11.0](https://github.com/thelightrixx/SimplePlots/releases/tag/v0.11.0) findest du die JARs und `SHA256SUMS.txt`. Prüfe den Hash der gewählten Datei vor der Installation.

<!-- SHARED:readme-build -->
Zum Bauen brauchst du JDK 25 oder neuer und Python ab 3.10. Unter Windows führt `build_all_jars.bat nobump ci` alle Prüfungen aus und stellt die Ergebnisse in `dist/` bereit. Maven wird gefunden oder als Version 3.9.11 heruntergeladen. Die [Entwicklungsanleitung](docs/index.html#/projektstandard) beschreibt auch den Build unter Linux und macOS. Ein Build erhöht keine Versionsnummer.
<!-- /SHARED:readme-build -->

## Lizenz

Plugin und Quellcode: [MIT](LICENSE). Die Lizenzhinweise der Handbuchschriften liegen unter [docs/licenses/](docs/licenses/). Die vollständigen Texte sind im [Handbuch](docs/index.html#/lizenz) auch offline enthalten.
