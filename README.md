# SimplePlots v0.2.0-alpha

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

## Bauen

SimplePlots ist für **Paper auf Minecraft 26.1.2 mit Java 25** gebaut. Der Build benötigt JDK 25 und Maven.

```sh
mvn -pl paper -am verify
```

Unter Windows steht alternativ `build_all_jars.bat` bereit. Die installierbare Datei ist `paper/target/SimplePlots-v0.2.0-alpha.jar`. Mehr zum Projektaufbau und zur Versionspflege steht unter [Entwicklung](docs/index.html#/entwicklung).
