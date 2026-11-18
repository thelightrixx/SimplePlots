"""Prüft die Offline-Dokumentation, ihre Navigation und lokalen Dateien."""

from html.parser import HTMLParser
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


PROJEKT = Path(__file__).resolve().parents[1]


class Dokumentation(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.seiten = {}
        self.seite = None
        self.links = []
        self.dateien = []
        self.fehler = []

    def handle_starttag(self, tag, attrs):
        werte = dict(attrs)
        if tag == "section" and "page" in werte.get("class", "").split():
            self.seite = werte.get("data-id")
            if not self.seite or self.seite in self.seiten:
                self.fehler.append(f"Fehlende oder doppelte Seiten-ID: {self.seite}")
            self.seiten.setdefault(self.seite, set())
            if not werte.get("data-title") or not werte.get("data-group"):
                self.fehler.append(f"Navigationstitel oder Gruppe fehlt bei {self.seite}.")
        anker = werte.get("data-anchor")
        if self.seite and anker:
            if anker in self.seiten[self.seite]:
                self.fehler.append(f"Doppelter Anker: {self.seite}/{anker}")
            self.seiten[self.seite].add(anker)
        if tag == "a" and "href" in werte:
            self.links.append(werte["href"])
        ressource = werte.get("src") if tag in {"script", "img", "iframe", "source"} else None
        if tag == "link":
            ressource = werte.get("href")
        if ressource and not ressource.startswith("data:"):
            if urlsplit(ressource).scheme or ressource.startswith("//"):
                self.fehler.append(f"Externe Ressource verhindert Offline-Nutzung: {ressource}")
            else:
                self.dateien.append(ressource)

    def handle_endtag(self, tag):
        if tag == "section":
            self.seite = None


def pruefen(projekt=PROJEKT):
    ordner = projekt / "docs"
    text = (ordner / "index.html").read_text(encoding="utf-8-sig")
    dokument = Dokumentation()
    dokument.feed(text)
    if not dokument.seiten:
        dokument.fehler.append("Keine Dokumentationsseiten gefunden.")
    for link in dokument.links:
        if link.startswith("#/"):
            teile = unquote(link[2:]).split("/")
            if teile[0] not in dokument.seiten or len(teile) > 2 or (
                len(teile) == 2 and teile[1] not in dokument.seiten.get(teile[0], set())
            ):
                dokument.fehler.append(f"Ungültiges Navigationsziel: {link}")
        elif not urlsplit(link).scheme and not link.startswith(("#", "//")):
            dokument.dateien.append(link)
    for datei in dokument.dateien:
        ziel = (ordner / unquote(urlsplit(datei).path)).resolve()
        if not ziel.is_relative_to(ordner.resolve()) or not ziel.is_file():
            dokument.fehler.append(f"Lokale Doku-Datei fehlt oder liegt außerhalb von docs/: {datei}")
    lizenz = ordner / "licenses/SimplePlots-MIT.txt"
    if lizenz.is_file() and lizenz.read_bytes() != (projekt / "LICENSE").read_bytes():
        dokument.fehler.append("Die MIT-Lizenz in docs/ stimmt nicht mit LICENSE überein.")
    if re.search(r"(?:url\(\s*['\"]?|@import\s+['\"])(?:https?:)?//", text, re.IGNORECASE):
        dokument.fehler.append("Externes Stylesheet oder Schrift verhindert Offline-Nutzung.")
    if dokument.fehler:
        raise SystemExit("\n".join(dokument.fehler))
    return len(dokument.seiten), len(dokument.links)


if __name__ == "__main__":
    try:
        seiten, links = pruefen()
        print(f"Dokumentation: {seiten} Seiten, {links} Links geprüft.")
    except (OSError, UnicodeError) as ursache:
        raise SystemExit(f"Dokumentationsprüfung fehlgeschlagen: {ursache}") from None
