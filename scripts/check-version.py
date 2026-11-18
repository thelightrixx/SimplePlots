"""Prüft die Versionsangaben in SimplePlots und seiner Dokumentation."""

import argparse
from pathlib import Path
import re
from xml.etree import ElementTree
from zipfile import BadZipFile, ZipFile


PROJEKT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
VERSION = r"v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"


def pruefen(jar_pruefen, tag):
    parent = ElementTree.parse(PROJEKT / "pom.xml").getroot()
    version = parent.findtext("m:version", default="", namespaces=NS).strip()
    if not re.fullmatch(VERSION, version):
        raise SystemExit(f"Ungültige Maven-Version: {version!r}. Erwartet wird vX.Y.Z, optional mit Vorabzusatz.")

    fehler = []
    for modul in ("common", "paper"):
        pfad = f"{modul}/pom.xml"
        pom = ElementTree.parse(PROJEKT / pfad).getroot()
        if pom.findtext("m:parent/m:version", namespaces=NS) != version:
            fehler.append(f"{pfad}: Parent-Version muss {version} sein.")
        if pom.findtext("m:version", default=version, namespaces=NS) != version:
            fehler.append(f"{pfad}: Modulversion muss {version} sein.")

    if tag is not None and tag != version:
        fehler.append(f"Git-Tag {tag} stimmt nicht mit Maven-Version {version} überein.")

    readme = (PROJEKT / "README.md").read_text(encoding="utf-8-sig")
    doku = (PROJEKT / "docs/index.html").read_text(encoding="utf-8-sig")
    jarname = f"SimplePlots-{version}.jar"
    changelog = re.search(r'<section class="page" data-id="changelog"[^>]*>(.*?)</section>', doku, re.DOTALL)
    eintraege = re.findall(r'<h2 data-anchor="([^"]+)">([^<]+)</h2>', changelog[1]) if changelog else []
    if readme.splitlines()[:1] != [f"# SimplePlots {version}"] or f"`paper/target/{jarname}`" not in readme:
        fehler.append(f"README.md: Titel und JAR-Dateiname müssen {version} enthalten.")
    if f'<span class="ver">{version}</span>' not in doku or f"<code>{jarname}</code>" not in doku:
        fehler.append(f"docs/index.html: Versionsanzeige und JAR-Dateiname müssen {version} enthalten.")
    if eintraege[:1] != [(version.replace(".", "-"), version)]:
        fehler.append(f"docs/index.html: Der neueste Changelog-Eintrag muss {version} sein.")

    if jar_pruefen:
        pfad = PROJEKT / "paper/target" / jarname
        try:
            with ZipFile(pfad) as jar:
                plugin = jar.read("plugin.yml").decode("utf-8").splitlines()
            if f"version: {version}" not in plugin:
                fehler.append(f"{jarname}: Abweichende Version in plugin.yml.")
        except (OSError, BadZipFile, KeyError, UnicodeError) as ursache:
            fehler.append(f"{jarname} konnte nicht geprüft werden: {ursache}")

    if fehler:
        raise SystemExit("\n".join(fehler))
    print(version)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", action="store_true", help="Auch das gebaute Paper-Plugin prüfen.")
    parser.add_argument("--tag", help="Erwartete Version aus dem Git-Tag.")
    argumente = parser.parse_args()
    try:
        pruefen(argumente.jar, argumente.tag)
    except (OSError, ElementTree.ParseError, UnicodeError) as ursache:
        raise SystemExit(f"Versionsprüfung fehlgeschlagen: {ursache}") from None
