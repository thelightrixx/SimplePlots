# SimplePlots v0.9.0

SimplePlots ergänzt Paper um Plotwelten mit öffentlichen Wegen, Claims und Baurechten. Spieler bauen im Survival-Modus mit ihrem eigenen Inventar. Sie können Freunde einladen, Nachbarplots verbinden, Besuchspunkte setzen und an Bauwettbewerben teilnehmen.

## Einrichten

| Server | Java | JAR-Endung |
|---|---|---|
| Paper/Purpur 1.21.8 | 21 | `paper-1.21.jar` |
| Paper/Purpur 26.1 | 25 | `paper.jar` |

Installiere genau eine Variante. Eine Datenbank ist nicht nötig. EconomySystem ist optional und wird für kostenpflichtige Claims direkt angebunden. Folia wird nicht unterstützt.

Lege das Raster fest, bevor die Plotwelt erzeugt wird. Standardmäßig sind Plots 32 × 32 Blöcke groß, die Wege sieben Blöcke breit und drei Claims pro Spieler erlaubt. Die [Installationsanleitung](docs/index.html#/installation) führt durch die Einrichtung und den Wechsel von älteren Projektnamen.

## Im Spiel

Mit `/plot world` betrittst du die Plotwelt, mit `/plot auto` beanspruchst du den nächsten freien Plot. `/plot help` zeigt die weiteren Befehle. Das [Spielerhandbuch](docs/index.html#/schnellstart) erklärt Baurechte, Besuchslisten und Menüs.

Sichere Plugin-Daten und Plotwelt gemeinsam. `plots.yml` und `plots-aenderungen.yml` bilden zusammen den gespeicherten Plotbestand; eine der beiden Dateien allein ist keine vollständige Sicherung.

Beim Update von v0.8.0-beta oder älter ist der Wechsel zu den neuen JAR-Dateinamen einmalig manuell nötig. Danach wählt der Updater die zur laufenden Plattform passende Variante. Näheres steht unter [Betrieb und Updates](docs/index.html#/betrieb).

## Nachlesen

Das [Handbuch](https://thelightrixx.github.io/SimplePlots/) enthält [Befehle](docs/index.html#/befehle), [Rechte](docs/index.html#/rechte), [Konfiguration](docs/index.html#/konfiguration) und Hinweise zu Sicherung und Wiederherstellung. `docs/index.html` funktioniert auch offline.

## Download und eigener Build

Im [Release v0.9.0](https://github.com/thelightrixx/SimplePlots/releases/tag/v0.9.0) findest du die JARs und `SHA256SUMS.txt`. Prüfe den Hash der gewählten Datei vor der Installation.

Zum Bauen brauchst du JDK 25 oder neuer und Python ab 3.10. Unter Windows führt `build_all_jars.bat nobump ci` alle Prüfungen aus und stellt die Ergebnisse in `dist/` bereit. Maven wird gefunden oder als Version 3.9.11 heruntergeladen. Die [Entwicklungsanleitung](docs/index.html#/projektstandard) beschreibt auch den Build unter Linux und macOS. Ein Build erhöht keine Versionsnummer.

## Lizenz

Plugin und Quellcode: [MIT](LICENSE). Die Lizenzhinweise der Handbuchschriften liegen unter [docs/licenses/](docs/licenses/).
