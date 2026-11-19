"""Prüft die Versionsangaben in SimplePlots und seiner Dokumentation."""

import argparse
from collections import Counter
from datetime import date
from pathlib import Path
import re
from xml.etree import ElementTree
from zipfile import BadZipFile, ZipFile


PROJEKT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
VERSION = r"v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"


def pruefen(jar_pruefen=False, tag=None, projekt=PROJEKT):
    parent = ElementTree.parse(projekt / "pom.xml").getroot()
    version = parent.findtext("m:version", default="", namespaces=NS).strip()
    if not re.fullmatch(VERSION, version):
        raise SystemExit(f"Ungültige Maven-Version: {version!r}. Erwartet wird vX.Y.Z, optional mit Vorabzusatz.")

    fehler = []
    for modul in ("common", "paper"):
        pfad = f"{modul}/pom.xml"
        pom = ElementTree.parse(projekt / pfad).getroot()
        if pom.findtext("m:parent/m:version", namespaces=NS) != version:
            fehler.append(f"{pfad}: Parent-Version muss {version} sein.")
        if pom.findtext("m:version", default=version, namespaces=NS) != version:
            fehler.append(f"{pfad}: Modulversion muss {version} sein.")

    if tag is not None and tag != version:
        fehler.append(f"Git-Tag {tag} stimmt nicht mit Maven-Version {version} überein.")

    readme = (projekt / "README.md").read_text(encoding="utf-8-sig")
    doku = (projekt / "docs/index.html").read_text(encoding="utf-8-sig")
    jarname = f"SimplePlots-{version}.jar"
    changelog = re.search(r'<section class="page" data-id="changelog"[^>]*>(.*?)</section>', doku, re.DOTALL)
    eintraege = re.findall(r'<h2 data-anchor="([^"]+)">([^<]+)</h2>', changelog[1]) if changelog else []
    if readme.splitlines()[:1] != [f"# SimplePlots {version}"] or f"`paper/target/{jarname}`" not in readme:
        fehler.append(f"README.md: Titel und JAR-Dateiname müssen {version} enthalten.")
    if f'<span class="ver">{version}</span>' not in doku or f"<code>{jarname}</code>" not in doku:
        fehler.append(f"docs/index.html: Versionsanzeige und JAR-Dateiname müssen {version} enthalten.")
    if eintraege[:1] != [(version.replace(".", "-"), version)]:
        fehler.append(f"docs/index.html: Der neueste Changelog-Eintrag muss {version} sein.")
    if tag is not None and eintraege:
        neuester = re.split(r'<h2\b[^>]*>[^<]*</h2>', changelog[1])[1]
        datum = re.search(r'<time datetime="(\d{4}-\d{2}-\d{2})">', neuester)
        try:
            if datum is None:
                raise ValueError("Datum fehlt")
            date.fromisoformat(datum[1])
        except ValueError:
            fehler.append("docs/index.html: Vor dem Taggen ein gültiges Veröffentlichungsdatum im neuesten Changelog-Eintrag setzen.")

    if jar_pruefen:
        pfad = projekt / "paper/target" / jarname
        try:
            with ZipFile(pfad) as jar:
                plugin = jar.read("plugin.yml").decode("utf-8").splitlines()
                erwartet = {
                    "name: SimplePlots",
                    f"version: {version}",
                    "main: de.lightrixx.plot.paper.SimplePlots",
                    "api-version: '26.1'",
                }
                if not erwartet.issubset(plugin):
                    fehler.append(f"{jarname}: Abweichende Plugin-Metadaten.")
                eintraege = jar.namelist()
                if any(anzahl > 1 for anzahl in Counter(eintraege).values()):
                    fehler.append(f"{jarname}: Doppelte ZIP-Einträge.")
                for name in ("de/lightrixx/plot/paper/SimplePlots.class",
                             "de/lightrixx/plot/service/PlotDienst.class",
                             "de/lightrixx/plot/service/WettbewerbDienst.class"):
                    if name not in eintraege:
                        fehler.append(f"{jarname}: Plugin-Klasse fehlt: {name}")
                for name in eintraege:
                    if name.startswith(("org/bukkit/", "io/papermc/", "net/kyori/", "org/slf4j/",
                                        "org/junit/", "org/mockito/", "org/yaml/", "net/bytebuddy/", "org/jacoco/")):
                        fehler.append(f"{jarname}: Server- oder Testbibliothek mitgeliefert: {name}")
                        break
                    if name.endswith(".class"):
                        kopf = jar.read(name)[:8]
                        if kopf[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(kopf[6:8], "big") != 65:
                            fehler.append(f"{jarname}: Kein Java-21-Bytecode in {name}")
                for name in ("config.yml", "config/messages.yml", "config/gui.yml"):
                    if jar.read(name) != (projekt / "paper/src/main/resources" / name).read_bytes():
                        fehler.append(f"{jarname}: Veraltete Ressource {name}")
                if jar.read("META-INF/LICENSE") != (projekt / "LICENSE").read_bytes():
                    fehler.append(f"{jarname}: MIT-Lizenz fehlt oder ist veraltet.")
                for modul in ("common", "paper"):
                    eigenschaften = jar.read(f"META-INF/maven/de.lightrixx/simpleplots-{modul}/pom.properties")
                    if f"version={version}" not in eigenschaften.decode("utf-8").splitlines():
                        fehler.append(f"{jarname}: Abweichende Maven-Version für {modul}.")
        except (OSError, BadZipFile, KeyError, UnicodeError) as ursache:
            fehler.append(f"{jarname} konnte nicht geprüft werden: {ursache}")

    if fehler:
        raise SystemExit("\n".join(fehler))
    return version


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", action="store_true", help="Auch das gebaute Paper-Plugin prüfen.")
    parser.add_argument("--tag", help="Erwartete Version aus dem Git-Tag.")
    argumente = parser.parse_args()
    try:
        print(pruefen(argumente.jar, argumente.tag))
    except (OSError, ElementTree.ParseError, UnicodeError) as ursache:
        raise SystemExit(f"Versionsprüfung fehlgeschlagen: {ursache}") from None
