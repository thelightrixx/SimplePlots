"""Stellt die geprüfte Plugin-JAR mit SHA-256 für eine Veröffentlichung bereit."""

import argparse
import hashlib
from html.parser import HTMLParser
from pathlib import Path
import re
import runpy
import shutil


PROJEKT = Path(__file__).resolve().parents[1]


class ReleaseText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.teile = []

    def handle_starttag(self, tag, attrs):
        if tag in {"p", "ul", "ol"}:
            self.teile.append("\n\n")
        elif tag == "li":
            self.teile.append("\n- ")
        elif tag == "code":
            self.teile.append("`")

    def handle_endtag(self, tag):
        if tag == "code":
            self.teile.append("`")

    def handle_data(self, data):
        self.teile.append(re.sub(r"\s+", " ", data))


def vorbereiten(projekt=PROJEKT, tag=None):
    version = runpy.run_path(str(projekt / "scripts/check-version.py"))["pruefen"](True, tag, projekt)
    quelle = projekt / "paper/target" / f"SimplePlots-{version}.jar"
    ordner = projekt / "dist"
    ordner.mkdir(exist_ok=True)
    ziel = ordner / quelle.name
    shutil.copyfile(quelle, ziel)
    sha = hashlib.sha256(quelle.read_bytes()).hexdigest()
    if hashlib.sha256(ziel.read_bytes()).hexdigest() != sha:
        raise SystemExit("Die kopierte JAR stimmt nicht mit dem geprüften Artefakt überein.")
    (ordner / "SHA256SUMS.txt").write_text(f"{sha}  {ziel.name}\n", encoding="utf-8", newline="\n")
    doku = (projekt / "docs/index.html").read_text(encoding="utf-8-sig")
    changelog = re.search(r'<section class="page" data-id="changelog"[^>]*>(.*?)</section>', doku, re.DOTALL)[1]
    eintrag = re.split(r'<h2\b[^>]*>[^<]*</h2>', changelog)[1]
    parser = ReleaseText()
    parser.feed(eintrag)
    text = "".join(parser.teile).strip()
    text = re.sub(r"\n[ \t]+", "\n", text)
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    (ordner / "RELEASE_NOTES.md").write_text(f"# SimplePlots {version}\n\n{text}\n", encoding="utf-8", newline="\n")
    return ziel


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", help="Erwartete Version für den Release-Tag.")
    argumente = parser.parse_args()
    try:
        print(vorbereiten(tag=argumente.tag))
    except (OSError, UnicodeError) as ursache:
        raise SystemExit(f"Release-Vorbereitung fehlgeschlagen: {ursache}") from None
