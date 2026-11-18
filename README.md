# SimplePlots

SimplePlots stellt auf einem Paper-Server eine separate Survival-Plotwelt bereit. Spieler behalten beim Weltwechsel ihr normales Inventar. Auf einem beanspruchten Plot bauen Besitzer, Trusted-Spieler und Bauhelfer mit ihren eigenen Materialien. Beim Betreten zeigt SimplePlots den Plotnamen oder die Plot-ID und darunter Besitzer und Beschreibung. Freie Plots zeigen einen Hinweis auf `/plot claim`. Die Texte lassen sich unter `betreten` in `config/messages.yml` anpassen.

## Installation

1. Die JAR aus `paper/target/` in den `plugins/`-Ordner eines Paper-Servers kopieren und den Server starten. Der Prototyp verwendet die Paper-API für Minecraft 26.1.2 und benötigt Java 25.
2. Für ein eigenes Raster vor dem ersten Besuch in der Konsole `plot admin grid 48 7` ausführen. Standardmäßig sind Plots 32 × 32 Blöcke groß, die Wege sieben Blöcke breit und pro Spieler sind drei Claims erlaubt.
3. Im Spiel `/plot auto` für den ersten Plot ausführen. `/plot world` führt zum Plotwelt-Spawn, `/plot back` zum Spawn der Hauptwelt.

Beim Wechsel von PlotSystem die bisherige JAR bei gestopptem Server durch die SimplePlots-JAR ersetzen. SimplePlots übernimmt `plugins/PlotSystem` beim Start als `plugins/SimplePlots`. Neue Plotwelten verwenden den Schlüssel `simpleplots:plots`. Bestehende Welten verwenden ihren bisherigen Schlüssel `plotsystem:plots`, damit die gespeicherten Spielerpositionen gültig bleiben.

## Befehle

| Befehl | Funktion |
| --- | --- |
| `/plot` | Hilfe anzeigen. `/p` ist ein Alias. |
| `/plot world`, `/plot back` | Zwischen Plotwelt und Spawn der Hauptwelt wechseln. |
| `/plot claim`, `/plot auto` | Den freien Plot unter dir beanspruchen oder einen freien Plot automatisch zugewiesen bekommen. |
| `/plot list`, `/plot info` | Eigene Plots auflisten oder den aktuellen Plot ansehen. |
| `/plot home [Nr]`, `/plot visit <Spieler\|X;Z> [Nr]` | Auf den Weg vor einen eigenen Plot oder einen fremden Plot teleportieren. Ohne Nummer gilt der erste Claim des Besitzers. |
| `/plot trust <Spieler>`, `/plot untrust <Spieler>` | Baurechte auf dem eigenen Plot erteilen oder entziehen. |
| `/plot deny <Spieler>`, `/plot undeny <Spieler>` | Zutritt sperren oder freigeben. `ban` und `unban` sind Aliase. Ein Bann entzieht vorhandene Trusted-Rechte. |
| `/plot kick <Spieler>` | Einen Spieler vom eigenen Plot auf den Weg versetzen. Der Spieler kann danach wieder eintreten. |
| `/plot menu`, `/plot discover [Kategorie] [new\|popular]` | Inventarmenü mit eigenen Plots, Rasterkarte und Rechteverwaltung öffnen oder die Galerie durchsuchen. |
| `/plot profile name <Name>`, `/plot profile description <Text\|->`, `/plot profile category <Kategorie\|off>` | Den Plot benennen, beschreiben und in der Galerie veröffentlichen. Kategorien sind `build`, `shop`, `museum`, `parkour` und `farm`. |
| `/plot visit #<Plotname>`, `/plot sethome [off]` | Einen benannten Plot besuchen oder einen Ankunftspunkt auf dem eigenen Plot setzen und entfernen. Landet der Spieler dort nicht sicher, führt der Befehl zum öffentlichen Weg vor dem Plot. |
| `/plot like`, `/plot unlike` | Auf einem veröffentlichten fremden Plot ein Like vergeben oder zurücknehmen. |
| `/plot flag <doors\|switches> <on\|off>` | Öffentliches Benutzen von Türen, Falltüren, Toren oder Schaltern erlauben. `switches` umfasst Knöpfe, Hebel und Druckplatten. |
| `/plot add <Spieler>`, `/plot unadd <Spieler>` | Bauhelfer dürfen bauen und den Plot benutzen, solange der Besitzer online ist. |
| `/plot storage <Spieler>`, `/plot unstore <Spieler>` | Zugriff auf Kisten, Fässer und Shulkerkisten separat verwalten. |
| `/plot merge <X;Z> [confirm]` | Eigenen Nachbarplot mit dem aktuellen Plot verbinden und den Innenweg zur Baufläche machen. |
| `/plot contest`, `/plot contest entries`, `/plot contest results [Seite]` | Status, Beiträge und Ergebnisse des Bauwettbewerbs anzeigen. |
| `/plot submit`, `/plot withdraw`, `/plot vote` | Eigenen Plot einreichen, ihn in der Bauphase zurückziehen oder auf einem fremden Beitrag abstimmen. |
| `/plot contest start <Thema>`, `/plot contest voting`, `/plot contest finish`, `/plot contest cancel` | Als Admin den Bauwettbewerb verwalten. |
| `/plot admin info`, `/plot admin create` | Das Raster anzeigen oder die Plotwelt aus der Konsole erstellen. |
| `/plot admin grid <Größe> <Wegbreite>`, `/plot admin limit <Anzahl>` | Raster für die erste Generierung festlegen oder das Claim-Limit ändern. |
| `/plot admin gamemode <creative\|survival>` | In der Plotwelt zwischen Kreativ- und Überlebensmodus wechseln. |

Für die Rechtebefehle musst du auf deinem eigenen Plot stehen. Verwende für Spieler einen Server-bekannten Namen oder die UUID. `simpleplots.use`, `simpleplots.claim` und `simpleplots.manage` sind standardmäßig für Spieler freigegeben. `simpleplots.admin` gilt standardmäßig für Operatoren und die Konsole.

Spieler mit `simpleplots.admin` können auch bei einem Plotbann eintreten und Plotrollen mit `/plot info` einsehen. Außerhalb gesperrter Wettbewerbsflächen dürfen sie oberhalb des geschützten Bodens bauen und Container benutzen, auch auf fremden Plots und öffentlichen Wegen. Wer den Kreativmodus über `/plot admin gamemode creative` einschaltet, wechselt beim Verlassen der Plotwelt wieder in Survival.

Das Raster wird bei der ersten Erstellung fixiert, damit neue Chunks an vorhandene Plots und Wege anschließen. Die Fläche beginnt über dem geschützten Boden bei Y=64. Steinziegelstufen markieren die beiden plotseitigen Ränder der öffentlichen Wege. SimplePlots ergänzt die Ränder beim Laden bestehender Chunks auf freien Positionen über dem ursprünglichen Wegboden. PvP und natürliches Mob-Spawning sind in der Plotwelt deaktiviert. Flüssigkeiten und Kolben bleiben innerhalb der Plotgrenzen.

## Zusammenlegen und Bauwettbewerbe

Beim Zusammenlegen beginnt der Besitzer auf dem Ausgangsplot. Dessen Profil bleibt das Profil der Gesamtfläche. Die Plotumrandung folgt der neuen Außengrenze. Gastfreigaben und erteilte Rollen gelten für die Gesamtfläche, wenn sie auf beiden Plots gesetzt waren. Ein Bann auf einem der beiden Plots gilt für die Gesamtfläche. Frühere Raster-IDs bleiben für `/plot visit` gültig. Jede belegte Rasterzelle zählt gegen das Claim-Limit.

Beim Bauwettbewerb kann jeder Besitzer einen Plot einreichen. Die Bauänderungen sind ab Einreichung gesperrt. In der Bauphase lässt sich der Beitrag zurückziehen. Während der Abstimmung hat jeder Spieler eine Stimme und darf sie bis zum Schluss ändern. Mit dem Ende oder Abbruch der Abstimmung sind die Flächen wieder bebaubar. Bei Stimmengleichheit zeigt das Ergebnis dieselbe Stimmenzahl für die betreffenden Plots.

## Daten und Entwicklung

`plugins/SimplePlots/plots.yml` enthält Claims, Profile und UUID-basierte Rechte. Beim Laden der ersten Version werden bestehende Claims als einzelne Rasterzellen mit ihren bisherigen Besitzern, Trusted-Spielern und Banns übernommen. Die nächste Plotänderung schreibt das erweiterte Format. `welt.yml` enthält das aktive Raster, `wettbewerb.yml` den aktuellen oder zuletzt beendeten Wettbewerb, `config.yml` die Einstellungen und `config/messages.yml` die MiniMessage-Texte.

Baue mit JDK 25 und `mvn -pl paper -am verify` oder unter Windows mit `build_all_jars.bat`. Das Modul `common` enthält Raster, Rollen, Claim- und Wettbewerbslogik sowie den Vertrag `PlotKauf`. `paper` bindet diese Logik an Paper an und erzeugt `paper/target/SimplePlots-0.2.0-alpha.jar`. Der Prototyp verwendet `PlotKauf.kostenlos()`. Der Einstieg für das spätere Geldsystem liegt nach der Claim-Prüfung und vor dem Speichern des Claims.
