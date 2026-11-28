# SimplePlots v0.9.0

Plotwelten mit Claims, Rollen, Galerie, Wettbewerben und reversibler Moderation.

## Handbuch

Lies das [SimplePlots Handbuch](https://thelightrixx.github.io/SimplePlots/) oder öffne [docs/index.html](docs/index.html) direkt im Browser. Navigation, Suche, Farbschema, Schriften und Druckansicht funktionieren offline.

| Aufgabe | Einstieg |
|---|---|
| Plugin einrichten | [Installation](docs/index.html#/installation) |
| Erste Schritte | [Schnellstart](docs/index.html#/schnellstart) |
| Syntax und Beispiele | [Befehle](docs/index.html#/befehle) |
| Betrieb und Sicherungen | [Betrieb](docs/index.html#/betrieb) |
| Änderungen nachlesen | [Changelog](docs/index.html#/changelog) |
| Bauen oder veröffentlichen | [Projektstandard](docs/index.html#/projektstandard) |

Mit Strg+K oder / durchsuchst du das Handbuch. Abschnittslinks führen direkt zur passenden Stelle. Die Druckansicht enthält das gesamte Handbuch mit geöffneten Details.

## Voraussetzungen und JARs

Das [Release v0.9.0](https://github.com/thelightrixx/SimplePlots/releases/tag/v0.9.0) enthält alle passenden JARs und SHA256SUMS.txt.

| Einsatzort | Voraussetzung | Variante |
|---|---|---|
| Backend | Paper/Purpur 1.21.8, Java 21 | paper-1.21 |
| Backend | Paper/Purpur 26.1, Java 25 | paper |

Genau eine passende Paper-JAR installieren. Beide Varianten enthalten dieselben Funktionen; sie werden getrennt gegen ihre jeweilige API gebaut. Vor Updates Datenordner und Welten sichern, alte JAR ersetzen und regulär neu starten. Folia wird nicht unterstützt.

Für den Wechsel von v0.8.0-beta auf v0.9.0 die passende JAR einmal manuell installieren, da sich der Dateiname durch die Plattformvarianten ändert. Anschließend unterstützt der Updater die neuen Namen.

## Funktionen und bestehende Betriebshinweise

SimplePlots ergänzt einen Paper-Server um mehrere getrennt konfigurierbare Survival-Plotwelten mit Claims, Baurechten, Plotgalerie und Bauwettbewerben. Spieler behalten beim Weltwechsel ihr Inventar.

v0.8.0-beta ergänzt weltbezogene Einstellungen unter `worlds/<Name>.yml`, die Standortwahl `~` für Verwaltungsbefehle sowie das vollständige Entfernen und leere Wiederherstellen von Plotflächen. Rechtebündel und eine Einrichtungsprüfung führen die passenden Bedienmuster aus PunishSystem fort. SimplePlots bleibt ein Paper-Plugin mit YAML-Speicherung.

Die Projektpflege folgt den gemeinsamen Mustern mit PunishSystem: kommentierte Konfigurationen mit automatischem Abgleich, Versions- und Diagnosebefehle, ein Offline-Handbuch und geprüfte Release-Artefakte. SimplePlots behält seine grüne Kennfarbe und die eigene Plotarchitektur.

### Handbuch

Lies das [SimplePlots Handbuch](https://thelightrixx.github.io/SimplePlots/) oder öffne [docs/index.html](docs/index.html) direkt im Browser. Die 29 Themen decken Einrichtung, Spielerabläufe, Verwaltung und Betrieb ab. Inhalte, Suche, Schriften und Rasterrechner funktionieren auch offline; für die Lizenzlinks den gesamten Ordner `docs/` mitnehmen.

| Aufgabe | Einstieg |
| --- | --- |
| SimplePlots einrichten oder von PlotSystem wechseln | [Installation](docs/index.html#/installation) |
| Mehrere Plotwelten einrichten | [Weltkonfiguration](docs/index.html#/welten) |
| Raster, Plot-IDs und Baugrenzen verstehen | [Plotwelt mit Koordinatenrechner](docs/index.html#/plotwelt) |
| Einen Plot beanspruchen und Freunde einladen | [Schnellstart](docs/index.html#/schnellstart), [Rollen und Zutritt](docs/index.html#/rollen) |
| Menüs bedienen oder Bauten veröffentlichen | [Menüs und Rasterkarte](docs/index.html#/menues), [Profile und Galerie](docs/index.html#/galerie) |
| Flächen verbinden oder den Besitzer wechseln | [Zusammenlegen und Übertragen](docs/index.html#/grundstuecke) |
| Flugregeln verstehen oder Wettbewerbe veranstalten | [Flug und Spielmodi](docs/index.html#/flug), [Bauwettbewerbe](docs/index.html#/wettbewerbe) |
| Syntax, Aliase, Rechte und Ausführungsort nachschlagen | [Filterbare Befehlsreferenz](docs/index.html#/befehle) |
| Rechte oder eine der acht Konfigurationsdateien ändern | [Berechtigungen](docs/index.html#/rechte), [Konfigurationsreferenz](docs/index.html#/konfiguration) |
| Fremde Claims verwalten oder Inhalte zensieren | [Plotverwaltung und Zensur](docs/index.html#/moderation) |
| Aktualisieren, sichern oder einen Fehler eingrenzen | [Updates und Sicherungen](docs/index.html#/betrieb), [Fehlerbehebung](docs/index.html#/fehlerbehebung), [FAQ](docs/index.html#/faq) |
| Speicherung, Threading und Prüfungen nachvollziehen | [Qualität und Architektur](docs/index.html#/qualitaet) |
| Versionsänderungen nachlesen | [Changelog](docs/index.html#/changelog) |

Mit `Strg+K` (macOS: `⌘+K`) oder `/` durchsuchst du das Handbuch einschließlich Befehlen, Permissions und Konfigurationsschlüsseln. Abschnittslinks an Überschriften führen direkt zur passenden Stelle. Die Druckfunktion des Browsers gibt das vollständige Handbuch aus und erlaubt das Speichern als PDF.

### Voraussetzungen und Installation

| Einsatz | Voraussetzung |
| --- | --- |
| Server | Paper für Minecraft **26.1.2**, Java **25** |
| Entwicklung | JDK **25 oder neuer**, Maven **3.9.11**, Python **3.10 oder neuer** |
| Windows-Werkzeuge | Windows PowerShell **5.1** oder PowerShell **7** |

Folia wird nicht unterstützt. Eine Datenbank und zusätzliche Plugins sind nicht erforderlich. Claims sind standardmäßig kostenlos; optional bucht SimplePlots einen Preis je Rasterzelle direkt über EconomySystem ab. Vault wird dafür nicht benötigt.

Für Preise wird EconomySystem über `softdepend` vor SimplePlots geladen. `wirtschaft.aktiv: true` schaltet die Abbuchung ein, `wirtschaft.preis` legt den Betrag je Rasterzelle fest. Reicht das Guthaben nicht oder fehlt die Anbindung, bleibt der Plot frei und es wird nichts gespeichert. Scheitert die Speicherung nach einer Abbuchung, erstattet SimplePlots den Betrag. Claims der Verwaltung bleiben kostenlos.

Im [Release v0.8.0-beta](https://github.com/thelightrixx/SimplePlots/releases/tag/v0.8.0-beta) findest du die [Plugin-JAR](https://github.com/thelightrixx/SimplePlots/releases/download/v0.8.0-beta/SimplePlots-v0.8.0-beta.jar) und die [SHA-256-Prüfsumme](https://github.com/thelightrixx/SimplePlots/releases/download/v0.8.0-beta/SHA256SUMS.txt).

1. Lade die JAR aus dem Release herunter oder [baue sie selbst](#bauen-und-prüfen). Prüfe beim Download die SHA-256 und kopiere `SimplePlots-v0.8.0-beta.jar` bei gestopptem Server nach `plugins/`.
2. Starte Paper und prüfe das Log mit dem grünen SP-Banner und der anschließenden Bereitschaftsmeldung. Die Konfiguration entsteht unter `plugins/SimplePlots/`.
3. Lege vor der ersten Weltgenerierung bei Bedarf das Raster fest, etwa mit `plot admin grid 48 7` in der Konsole.
4. Erstelle die Welt mit `plot admin create`. Im Spiel öffnet `/plot world` die Plotwelt; `/plot claim` beansprucht den Plot unter dir.

Standardmäßig gelten 32 × 32 Blöcke pro Rasterzelle, sieben Blöcke Wegbreite und drei belegte Rasterzellen pro Spieler. Zusammengelegte Zellen zählen weiterhin einzeln gegen das Limit. Das Raster bleibt nach der ersten Generierung fest. `/plot admin info` zeigt die aktiven Werte. `/plot auto` sucht und beansprucht direkt eine freie Fläche und bringt dich nach erfolgreicher Speicherung dorthin.

`/plot back` führt aus der Plotwelt zum letzten Standort in der Hauptwelt `minecraft:overworld` zurück, einschließlich Blickrichtung. Der Ort wird beim Verlassen der Hauptwelt pro Spieler gespeichert und bleibt über Neustarts erhalten. Ohne gültigen gespeicherten Ort oder außerhalb der aktuellen Weltgrenze wird der Hauptwelt-Spawn verwendet. Vor dem Update liegende Aufenthalte können nicht nachträglich ermittelt werden.

Neue Plotflächen bestehen aus Gras auf Y=63, Erde auf Y=60–62 und Stein darunter. Nur die unterste Schicht auf Y=-64 bleibt Bedrock. Besitzer und Spieler mit Baurechten können auf beanspruchten Plots bis Y=-63 bauen und abbauen, den Grasboden bearbeiten und sichere Besuchspunkte unterirdisch setzen. Öffentliche Wege und Wettbewerbsflächen bleiben geschützt. Bereits erzeugte Chunks behalten ihren bisherigen Untergrund; es findet keine automatische Umwandlung statt.

LuckPerms wird nicht benötigt. Eigentum und Plotrollen werden anhand der Spieler-UUID geprüft; die SimplePlots-Befehlsrechte lassen sich über die normalen Paper-Permissions verwalten.

Mit `/plot admin diagnostics` prüfst du Version, Welt, Plotbestand und Speicherwarteschlange. `/plot admin reload` übernimmt alle acht YAML-Dateien unter `config/` gemeinsam, sobald sie gültig sind. Alte `config.yml`, `messages.yml` und `gui.yml` im Hauptordner werden beim Start übernommen und nach erfolgreicher Prüfung samt Vorgängersicherungen unter `config/altbestand/` archiviert. Angepasste Dateien unter `config/` behalten Vorrang; unveränderte Standardnachrichten und Standardmenüs übernehmen die Altwerte. Spielstände wie `plots.yml`, `plots-aenderungen.yml` und `welt.yml` bleiben im Hauptordner. Menüplätze, Seitengröße und Item-Materialien stehen in `config/gui.yml`; Texte bleiben in `config/messages.yml`.

Mit `simpleplots.admin` sind in der Plotwelt Survival, Creative, Adventure und Spectator erlaubt, auch über `/plot admin gamemode <survival|creative|adventure|spectator>`. `/plot fly on` erlaubt Admins in Survival und Adventure Flug über der gesamten Plotwelt ohne Höhenlimit; Creative und Spectator behalten ihren normalen Flug. `/plot fly off` beendet Admin-Fly; Wege-Fly bleibt gemäß `config/fly.yml` verfügbar.

Mit `/plot fly on|off` schaltest du Fly auf eigenen Plots; die Standardgrenze liegt bei Y=96 (32 Blöcke über Y=64). Auf öffentlichen Wegen können Survival-Spieler ohne Zeit- oder Höhenlimit per Doppelsprung starten. Interne Wege zusammengelegter Plots zählen zur eigenen Plotfläche.

`/plot transfer <Spieler>` bietet den aktuellen eigenen Plot einem sichtbaren Online-Spieler an. `/plot accept <X;Z>` nimmt standardmäßig innerhalb von 120 Sekunden an; das Claim-Limit berücksichtigt alle Rasterzellen. Rollen, Banns, Gastfreigaben und Likes werden zurückgesetzt; Bauten und Profil bleiben erhalten. Ändert sich der angebotene Plot inzwischen, muss ein neues Angebot erstellt werden.

`/plot unlink` zeigt die Folgen einer Merge-Auflösung; `/plot unlink confirm` (Alias `unmerge`) teilt den Claim atomar in einzelne Claims desselben Besitzers auf. Rechte und Bauten bleiben erhalten, das Profil bleibt an der Haupt-ID und der Besuchspunkt wird zurückgesetzt. Bauten auf ehemaligen Innenwegen bleiben erhalten und sind dort öffentlich geschützt. Für Zensuren und aktive Wettbewerbsbeiträge ist die Aufteilung gesperrt.

`/plot biome desert` ändert das Biom des eigenen Claims, etwa für Gras-, Laub- und Wasserfarben oder Wetter. Tab zeigt die registrierten Biome, `off` setzt die Vorgabe auf Ebene zurück. Minecraft speichert Biome in Zellen von 4 × 4 × 4 Blöcken: Nur vollständig zum Claim gehörende Spalten werden geändert; Wege und Nachbarplots bleiben unberührt. Die Änderung wird zuerst gespeichert und anschließend auf geladene Chunks verteilt; ungeladene folgen beim Laden.

`/plot admin censor <X;Z oder ~>` sichert den Plot und blendet Bauten sowie Entities aus. Der leere Plot bleibt anschließend mit roten Slabs und Eintrittshinweis begehbar, ist aber für alle gesperrt. `off` stellt den Inhalt wieder her. Die Sicherungen unter `zensur/` gehören zum vollständigen Plugin-Datenbestand. Die Arbeit bündelt bis zu 32 Sicherungsabschnitte pro Speicherauftrag und begrenzt die Weltarbeit standardmäßig auf acht Abschnitte und vier Millisekunden je Tick; ein einzelner Abschnitt wird vollständig beendet und kann das Zeitbudget überschreiten. Währenddessen bleiben Spieler auf den Wegen. Abgeschlossene Zensuren werden beim Neustart nicht erneut aufgebaut.

`/plot admin update check|status` prüft GitHub-Releases. `apply confirm` lädt eine passende JAR mit SHA-256-Prüfung in Papers Update-Ordner. Paper übernimmt sie beim nächsten normalen Start; das Plugin führt keinen Neustart aus. Der Standardkanal `beta` berücksichtigt Beta-, RC- und stabile Releases; `alpha` zusätzlich Alpha-Versionen, `stable` ausschließlich stabile Releases. Bestehende Kanalwerte bleiben erhalten. Erstprüfungen und Intervall lassen sich in `config/updates.yml` ändern.

Plot- und Wettbewerbsänderungen werden außerhalb des Serverthreads gespeichert. Die Bestätigung und die neuen Rechte erscheinen nach erfolgreicher Speicherung. `plots.yml` und `plots-aenderungen.yml` bilden gemeinsam den Plotbestand und müssen zusammen gesichert werden. Ältere Plotformate werden beim ersten Schreiben übernommen. Ein Downgrade benötigt die vollständige Sicherung einschließlich Welt vor dem Update; ältere Plugins verstehen die Zensursperren nicht.

Beim Wechsel von PlotSystem zuerst den Plugin-Datenordner und die Plotwelt gemeinsam sichern und die alte JAR entfernen. Die [Wechselanleitung](docs/index.html#/installation/wechsel) beschreibt die automatische Datenübernahme. Updates und Sicherungen stehen unter [Betrieb](docs/index.html#/betrieb).

Beim Start und Reload werden fehlende Optionen und aktuelle Standardkommentare in den acht Konfigurationsdateien ergänzt. Eigene Werte, Texte, Listen und Zusatzschlüssel bleiben erhalten. Vor tatsächlichen Änderungen entsteht eine `.bak`-Vorgängersicherung. Leere oder ungültige YAML-Dateien werden gemeldet und bleiben unverändert. `/plot admin diagnostics` zeigt zusätzlich Zensuranzahl und Arbeitsbudget; `/plot version` steht mit dem Basisrecht auch in der Konsole bereit.

### Mehrere Welten und Verwaltung

Beim ersten Start entsteht `worlds/plots.yml`; vorhandene Plotdaten und die Weltidentität bleiben erhalten. Weitere Welten bekommen eine Datei wie `worlds/creative.yml`:

```yaml
world:
  raster:
    plotgroesse: 48
    wegbreite: 9
general:
  claims:
    pro-spieler: 10
fly:
  eigener-plot:
    hoehe: 80
```

Die Abschnitte `general`, `world`, `protection`, `fly` und `contests` überschreiben die entsprechenden globalen Vorgaben unter `config/`. Fehlende Werte werden geerbt. Texte, Menülayouts, Updates, Rechtebündel und die Standardwelt bleiben global. Neue Weltdateien werden beim nächsten regulären Pluginstart registriert; `/plot admin reload` übernimmt Änderungen aller registrierten Welten gemeinsam und verwirft den Reload bei einem ungültigen Wert oder geänderten bestehenden Raster.

`/plot world creative` betritt eine Welt, `/plot worlds` zeigt die Namen. Befehle beziehen sich auf die aktuelle Plotwelt, außerhalb davon auf `general.yml: welten.standard` (Vorgabe `plots`). Mit `/plot in creative admin create` lässt sich eine Welt auch aus der Konsole vorbereiten. Claims, Limits, Namen, Galerien und Wettbewerbe sind je Welt getrennt. Zusätzliche Welten speichern unter `data/<Name>/`; `plots` behält die bisherigen Dateien im Plugin-Hauptordner.

`/plot admin remove <X;Z oder ~>` entfernt die ganze Fläche einschließlich interner Merge-Wege, Besitz, Rollen, Profil, Bauten und Entities bis ins Leere. Sie bleibt dauerhaft unclaimbar, auch für Admin-Claims und `/plot auto`. `/plot admin restore <X;Z oder ~>` erzeugt daraus freie Standardplots; früherer Inhalt und Besitz werden nicht zurückgeholt. Öffentliche Wege und Nachbarplots bleiben erhalten. Zensierte Plots und aktive Wettbewerbsbeiträge müssen vorher regulär entsperrt werden. Unterbrochene Arbeiten bleiben gespeichert und werden beim Start fortgesetzt. Die Arbeitsgrenzen stehen in `protection.yml` unter `zensur-*` und gelten auch für Remove/Restore.

`~` wählt die Fläche am Standort, auch bei Censor/Uncensor und anderen Verwaltungsbefehlen. Auf öffentlichen Wegen und in der Konsole wird `~` abgewiesen. Koordinaten verwenden weiterhin Plot-IDs im Format `X;Z`, keine Blockkoordinaten.

Die neuen Einzelrechte sind `simpleplots.admin.remove`, `simpleplots.admin.restore` und `simpleplots.admin.setup`; `simpleplots.admin` enthält sie. Unter `general.yml: rang-buendel` stehen anpassbare Bündel für Spieler, Supporter, Moderatoren und Admins. Zum Beispiel gewährt `simpleplots.rang.moderator` die konfigurierten Moderationsrechte. `/plot admin setup` prüft die registrierten Konfigurationen, `/plot admin diagnostics` ergänzt den ausgewählten Welt- und Entfernungszustand.

Vor dem Update alle Plugin-Daten sowie alle Plotwelten gemeinsam sichern. Plotformate 1 bis 3 bleiben lesbar; neue Änderungen verwenden Format 4 mit atomar gespeicherten Entfernungsaufträgen. `plots.yml` und `plots-aenderungen.yml` gehören je Welt zusammen. Ein Downgrade benötigt den vollständigen Stand vor dem Update.

### Bauen und prüfen

Unter Windows:

```bat
build_all_jars.bat
```

Der Aufruf funktioniert unabhängig vom aktuellen Arbeitsverzeichnis. Er prüft JDK und Python, nutzt Maven aus `SIMPLEPLOTS_MVN`, dem `PATH` oder dem eigenen `.tools/`-Ordner und lädt dort bei Bedarf Maven 3.9.11 mit festgelegter SHA-512-Prüfsumme. Er benötigt keine benachbarten Projekte. Die bisherigen Aufrufzusätze `nobump` und `ci` bleiben verwendbar; die Version wird bei lokalen Builds nicht geändert.

Der Build prüft Versionen und Doku, führt die Werkzeugtests und `mvn -pl paper -am clean verify` aus und kontrolliert die fertige JAR. Unter `dist/` liegen anschließend:

- `SimplePlots-v0.8.0-beta.jar` – installierbares Plugin.
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

Die Maven-Ausgabe bleibt `paper/target/SimplePlots-v0.8.0-beta.jar`. Für gezielte Kerntests genügt `mvn -pl common -am test`. Der vollständige Build führt alle Tests aus; schnelle Maven-Aufrufe für die Entwicklung sind separat möglich.

`mvn -Pcoverage -pl paper -am clean verify` erzeugt zusätzlich JaCoCo-Berichte unter `common/target/site/jacoco/` und `paper/target/site/jacoco/`. Die CI führt diese Messung auf Windows und Linux aus und stellt die Berichte als Artefakte bereit. Die [Prüfbereiche und Grenzen](docs/index.html#/qualitaet/pruefungen) sind im Handbuch beschrieben.

### Lokal auf Paper testen

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-test.ps1 -PrepareOnly
```

Das Skript baut das Plugin und bereitet unter `test-server/` genau den stabilen Paper-Build aus `pom.xml` vor. Der Download wird mit SHA-256 geprüft. Beim ersten Aufruf entstehen eine Konfiguration für `127.0.0.1:25585`, Online-Modus und `eula=false`. Bestehende Einstellungen und Welten bleiben erhalten.

Lies die [Minecraft-EULA](https://aka.ms/MinecraftEULA). Wenn du zustimmst, starte anschließend:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev-test.ps1 -NoBuild -AcceptEula
```

`-NoBuild` verwendet die bereits geprüfte JAR. `-Ram 3G`, `-ServerPort 25586` und `-OfflineMode` konfigurieren den ersten Start; spätere Änderungen erfolgen in `test-server/server.properties`. Beende den Server mit `stop`. Teste anschließend anhand der [Prüfschritte](docs/index.html#/entwicklung/laufzeittest) auch Weltwechsel, Claims, Rechte und einen Neustart.

### Projektordner

| Pfad | Zweck |
| --- | --- |
| `common/` | Plattformneutrale Modelle, Plot- und Wettbewerbslogik mit JUnit-Tests. |
| `paper/` | Plugin, Weltgenerierung, Befehle, Listener, YAML-Speicher und Tests. |
| `docs/` | Offline-Handbuch mit Suche, Changelog und Schriftlizenzen. |
| `scripts/` | Build, Werkzeugverwaltung, Testserver, Versions- und Artefaktprüfungen. |
| `.github/workflows/` | Prüfungen auf Windows/Linux, Release-Vorbereitung und GitHub Pages. |
| `.mvn/` | Markiert die Maven-Projektwurzel und setzt UTF-8 für den Build-Prozess. |
| `.tools/` | Lokale Werkzeuge, temporäre Prüfprogramme, isolierte Testinstanzen und Prüfprotokolle; ignoriert. |
| `test-server/` | Lokaler Paper-Testserver samt Welten und Plugin-Daten; ignoriert. |
| `dist/`, `*/target/` | Geprüfte Ausgabe bzw. Maven-Builddaten; ignoriert. |
| `.gitignore` | Hält erzeugte Dateien, lokale Werkzeuge, IDE-Einstellungen und Secrets aus der Versionsverwaltung. |
| `AGENTS.md` | Lokale Arbeitsregeln für Architektur, Speicherung, Prüfungen und Releases; ignoriert. |

`.editorconfig` und `.gitattributes` legen UTF-8 und Zeilenenden fest. `AGENTS.md`, IDE-Einstellungen, Secrets und erzeugte Dateien bleiben lokal. Zur Versionspflege und Veröffentlichung siehe [Entwicklung](docs/index.html#/entwicklung/versionen).

### Lizenz

Plugin und Quellcode stehen unter der [MIT-Lizenz](LICENSE). Die eingebetteten Dokumentationsschriften JetBrains Mono und Poppins stehen jeweils unter der SIL Open Font License 1.1; die vollständigen Hinweise liegen unter [docs/licenses/](docs/licenses/).

## Entwicklung und Veröffentlichungen

Aufbau und Abläufe folgen PunishSystem: gemeinsamer Kern in common/, Plattformadapter in eigenen Modulen, deutsches Offline-Handbuch unter docs/ und Werkzeuge unter scripts/. Fachlich notwendige Zusatzmodule bleiben erhalten.

```powershell
build_all_jars.bat nobump ci
# Alternativ mit vorhandenem Maven:
mvn -B -ntp clean verify
python scripts/check-version.py
python scripts/check-docs.py
python scripts/prepare-release.py
```

Build: JDK 25 oder neuer, Maven 3.9.11 und Python 3.10 oder neuer. Java-Bytecode: Release 21. Geprüfte JARs, Release-Text und Prüfsummen liegen unter dist/. Gewöhnliche Builds ändern keine Version.

Versionen heißen vMajor.Minor.Patch. Patch ist die Vorgabe für Korrekturen und kleine Erweiterungen, Minor für umfassende Änderungen. Mit scripts/bump-build.ps1 ausdrücklich erhöhen, danach README, Handbuch und Changelog aktualisieren. GitHub Actions prüft main unter Windows und Linux; nach erfolgreichem Build und Pages-Deployment erstellt ein passender Versions-Tag den stabilen Release.

Kompilierung und automatisierte Tests ersetzen keine Spielertests auf beiden Servervarianten. Bestehende Daten, Rechte, Plugin-Abhängigkeiten und Updatepfade vor dem Produktiveinsatz auf einer getrennten Testinstanz prüfen.

## Lizenz

Plugin und Quellcode stehen unter der [MIT-Lizenz](LICENSE). Die Handbuchgestaltung stammt aus PunishSystem; ihre MIT-Lizenz und die eingebetteten Schriftlizenzen bleiben erhalten.
