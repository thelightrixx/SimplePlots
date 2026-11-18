# SimplePlots v0.2.1-alpha

SimplePlots ergänzt einen Paper-Server um eine separate Survival-Plotwelt mit Claims, Baurechten, Plotgalerie und Bauwettbewerben. Spieler behalten beim Weltwechsel ihr Inventar.

## Dokumentation

Öffne [docs/index.html](docs/index.html) im Browser. Die Dokumentation mit Suche und Changelog funktioniert auch offline.

| Aufgabe | Einstieg |
| --- | --- |
| SimplePlots einrichten oder von PlotSystem wechseln | [Installation](docs/index.html#/installation) |
| Einen Plot beanspruchen und Baurechte vergeben | [Schnellstart](docs/index.html#/schnellstart) |
| Syntax, Aliase und Optionen nachschlagen | [Befehlsreferenz](docs/index.html#/befehle) |
| Rechte und Konfiguration ändern | [Berechtigungen](docs/index.html#/rechte), [Konfiguration](docs/index.html#/konfiguration) |
| Spielstände sichern oder Fehler beheben | [Betrieb](docs/index.html#/betrieb) |
| Versionsänderungen nachlesen | [Changelog](docs/index.html#/changelog) |

## Voraussetzungen und Installation

| Einsatz | Voraussetzung |
| --- | --- |
| Server | Paper für Minecraft **26.1.2**, Java **25** |
| Entwicklung | JDK **25 oder neuer**, Maven **3.9.11**, Python **3.10 oder neuer** |
| Windows-Werkzeuge | Windows PowerShell **5.1** oder PowerShell **7** |

Folia wird nicht unterstützt. Eine Datenbank und zusätzliche Plugins sind für den aktuellen Funktionsumfang nicht erforderlich. Claims sind derzeit kostenlos.

Das [Release v0.2.1-alpha](https://github.com/thelightrixx/SimplePlots/releases/tag/v0.2.1-alpha) enthält die [Plugin-JAR](https://github.com/thelightrixx/SimplePlots/releases/download/v0.2.1-alpha/SimplePlots-v0.2.1-alpha.jar) und [SHA256SUMS.txt](https://github.com/thelightrixx/SimplePlots/releases/download/v0.2.1-alpha/SHA256SUMS.txt).

1. Lade die JAR aus dem Release herunter oder [baue sie selbst](#bauen-und-prüfen). Kopiere `SimplePlots-v0.2.1-alpha.jar` bei gestopptem Server nach `plugins/` und prüfe beim Download die SHA-256.
2. Starte Paper und prüfe das Log. Die Konfiguration entsteht unter `plugins/SimplePlots/`.
3. Lege vor der ersten Weltgenerierung bei Bedarf das Raster fest, etwa mit `plot admin grid 48 7` in der Konsole.
4. Erstelle die Welt mit `plot admin create`. Im Spiel öffnet `/plot world` die Plotwelt; `/plot claim` beansprucht den Plot unter dir.

Standardmäßig gelten 32 × 32 Blöcke pro Plot, sieben Blöcke Wegbreite und drei Claims pro Spieler. Das Raster bleibt nach der ersten Generierung fest. `/plot admin info` zeigt die aktiven Werte.

`/plot back` führt aus der Plotwelt zum Spawn der Hauptwelt `minecraft:overworld`. Das Rückkehrziel hängt nicht von der Ladereihenfolge der Welten ab.

Beim Wechsel von PlotSystem zuerst den Plugin-Datenordner und die Plotwelt gemeinsam sichern und die alte JAR entfernen. Die [Wechselanleitung](docs/index.html#/installation/wechsel) beschreibt die automatische Datenübernahme. Updates und Sicherungen stehen unter [Betrieb](docs/index.html#/betrieb).

## Bauen und prüfen

Unter Windows:

```bat
build_all_jars.bat
```

Der Aufruf funktioniert unabhängig vom aktuellen Arbeitsverzeichnis. Er prüft JDK und Python, nutzt Maven aus `SIMPLEPLOTS_MVN`, dem `PATH` oder dem eigenen `.tools/`-Ordner und lädt dort bei Bedarf Maven 3.9.11 mit festgelegter SHA-512-Prüfsumme. Er benötigt keine benachbarten Projekte. Die bisherigen Aufrufzusätze `nobump` und `ci` bleiben verwendbar; die Version wird bei lokalen Builds nicht geändert.

Der Build prüft Versionen und Doku, führt die Werkzeugtests und `mvn -pl paper -am clean verify` aus und kontrolliert die fertige JAR. Unter `dist/` liegen anschließend:

- `SimplePlots-v0.2.1-alpha.jar` – installierbares Plugin.
- `SHA256SUMS.txt` – SHA-256 der exakt geprüften JAR.
- `RELEASE_NOTES.md` – Veröffentlichungstext aus dem aktuellen Changelog.

Für einen manuellen Build auf Windows, Linux oder macOS:

```sh
python scripts/check-version.py
python scripts/check-docs.py
python -m unittest discover -s scripts -p 'test_*.py'
mvn -pl paper -am clean verify
python scripts/check-version.py --jar
python scripts/prepare-release.py
```

Die Maven-Ausgabe bleibt `paper/target/SimplePlots-v0.2.1-alpha.jar`. Für gezielte Kerntests genügt `mvn -pl common -am test`. Der vollständige Build führt alle Tests aus; schnelle Maven-Aufrufe für die Entwicklung sind separat möglich.

## Lokal auf Paper testen

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-test.ps1 -PrepareOnly
```

Das Skript baut das Plugin und bereitet unter `test-server/` genau den stabilen Paper-Build aus `pom.xml` vor. Der Download wird mit SHA-256 geprüft. Beim ersten Aufruf entstehen eine Konfiguration für `127.0.0.1:25585`, Online-Modus und `eula=false`. Bestehende Einstellungen und Welten bleiben erhalten.

Lies die [Minecraft-EULA](https://aka.ms/MinecraftEULA). Wenn du zustimmst, starte anschließend:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-test.ps1 -NoBuild -AcceptEula
```

`-NoBuild` verwendet die bereits geprüfte JAR. `-Ram 3G`, `-ServerPort 25586` und `-OfflineMode` konfigurieren den ersten Start; spätere Änderungen erfolgen in `test-server/server.properties`. Beende den Server mit `stop`. Teste anschließend anhand der [Prüfschritte](docs/index.html#/entwicklung/laufzeittest) auch Weltwechsel, Claims, Rechte und einen Neustart.

## Projektordner

| Pfad | Zweck |
| --- | --- |
| `common/` | Plattformneutrale Modelle, Plot- und Wettbewerbslogik mit JUnit-Tests. |
| `paper/` | Plugin, Weltgenerierung, Befehle, Listener, YAML-Speicher und Tests. |
| `docs/` | Offline-Handbuch mit Suche, Changelog und Schriftlizenzen. |
| `scripts/` | Build, Werkzeugverwaltung, Testserver, Versions- und Artefaktprüfungen. |
| `.github/workflows/` | Prüfungen auf Windows/Linux, Release-Vorbereitung und GitHub Pages. |
| `.mvn/` | Markiert die Maven-Projektwurzel und setzt UTF-8 für den Build-Prozess. |
| `.tools/` | Lokales Maven und temporäre Werkzeugdaten; ignoriert. |
| `test-server/` | Lokaler Paper-Testserver samt Welten und Plugin-Daten; ignoriert. |
| `dist/`, `*/target/` | Geprüfte Ausgabe bzw. Maven-Builddaten; ignoriert. |

`.editorconfig` und `.gitattributes` legen UTF-8 und Zeilenenden fest. `AGENTS.md`, IDE-Einstellungen, Secrets und erzeugte Dateien bleiben lokal. Zur Versionspflege und Veröffentlichung siehe [Entwicklung](docs/index.html#/entwicklung/versionen).

## Lizenz

Plugin und Quellcode stehen unter der [MIT-Lizenz](LICENSE). Die eingebetteten Dokumentationsschriften JetBrains Mono und Poppins stehen jeweils unter der SIL Open Font License 1.1; die vollständigen Hinweise liegen unter [docs/licenses/](docs/licenses/).
