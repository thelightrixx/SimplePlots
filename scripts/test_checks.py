"""Regressionstests für Dokumentations- und Artefaktprüfungen ohne Zusatzpakete."""

import hashlib
from pathlib import Path
import runpy
import shutil
import tempfile
import unittest
import warnings
from zipfile import ZipFile


SKRIPTE = Path(__file__).resolve().parent
version_pruefen = runpy.run_path(str(SKRIPTE / "check-version.py"))["pruefen"]
doku_pruefen = runpy.run_path(str(SKRIPTE / "check-docs.py"))["pruefen"]
release_vorbereiten = runpy.run_path(str(SKRIPTE / "prepare-release.py"))["vorbereiten"]


class ProjektPruefungenTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.projekt = Path(self.temp.name)
        self.version = "v0.2.0-alpha"
        self.schreiben("pom.xml", self.pom(f"<version>{self.version}</version>"))
        for modul in ("common", "paper"):
            self.schreiben(f"{modul}/pom.xml", self.pom(f"<parent><version>{self.version}</version></parent>"))
        self.schreiben("README.md", f"# SimplePlots {self.version}\n`paper/target/SimplePlots-{self.version}.jar`\n")
        self.doku = f'''<span class="ver">{self.version}</span>
<section class="page" data-id="changelog" data-title="Changelog" data-group="Betrieb">
<h2 data-anchor="v0-2-0-alpha">{self.version}</h2><p><time datetime="2026-11-18">18.11.2026</time>. Geprüfter Stand.</p>
<ul><li>Build mit <code>verify</code>.</li></ul><code>SimplePlots-{self.version}.jar</code>
<a href="#/changelog/v0-2-0-alpha">Änderungen</a></section>'''
        self.schreiben("docs/index.html", self.doku)
        self.schreiben("LICENSE", "MIT License\n")
        self.schreiben("paper/src/main/resources/config.yml", "claims: {}\n")
        self.schreiben("paper/src/main/resources/config/messages.yml", "prefix: 'SimplePlots'\n")
        self.schreiben("paper/src/main/resources/config/gui.yml", "hauptmenue: {}\n")
        self.klassen = {
            "de/lightrixx/plot/paper/SimplePlots.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x41",
            "de/lightrixx/plot/service/PlotDienst.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x41",
            "de/lightrixx/plot/service/WettbewerbDienst.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x41",
        }

    @staticmethod
    def pom(inhalt):
        return f'<project xmlns="http://maven.apache.org/POM/4.0.0">{inhalt}</project>'

    def schreiben(self, pfad, text):
        ziel = self.projekt / pfad
        ziel.parent.mkdir(parents=True, exist_ok=True)
        ziel.write_text(text, encoding="utf-8", newline="\n")

    def jar(self, aenderungen=None):
        dateien = {
            **self.klassen,
            "plugin.yml": f"name: SimplePlots\nversion: {self.version}\nmain: de.lightrixx.plot.paper.SimplePlots\napi-version: '26.1'\n".encode(),
            "config.yml": b"claims: {}\n",
            "config/messages.yml": b"prefix: 'SimplePlots'\n",
            "config/gui.yml": b"hauptmenue: {}\n",
            "META-INF/LICENSE": b"MIT License\n",
            **{f"META-INF/maven/de.lightrixx/simpleplots-{modul}/pom.properties": f"version={self.version}\n".encode()
               for modul in ("common", "paper")},
        }
        dateien.update(aenderungen or {})
        ziel = self.projekt / f"paper/target/SimplePlots-{self.version}.jar"
        ziel.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(ziel, "w") as jar:
            for name, inhalt in dateien.items():
                if inhalt is not None:
                    jar.writestr(name, inhalt)
        return ziel

    def test_konsistente_version_und_vollstaendige_jar(self):
        self.jar()
        self.assertEqual(self.version, version_pruefen(True, self.version, self.projekt))

    def test_abweichender_tag_wird_abgewiesen(self):
        with self.assertRaisesRegex(SystemExit, "Git-Tag"):
            version_pruefen(False, "v0.2.1-alpha", self.projekt)

    def test_abweichende_modulversion_wird_abgewiesen(self):
        self.schreiben("common/pom.xml", self.pom("<parent><version>v0.1.0-alpha</version></parent>"))
        with self.assertRaisesRegex(SystemExit, "Parent-Version"):
            version_pruefen(projekt=self.projekt)

    def test_release_tag_erfordert_ein_gueltiges_veroeffentlichungsdatum(self):
        for ersatz in ('<span>', '<time datetime="2026-02-30">'):
            with self.subTest(ersatz=ersatz):
                self.schreiben("docs/index.html", self.doku.replace('<time datetime="2026-11-18">', ersatz))
                with self.assertRaisesRegex(SystemExit, "Veröffentlichungsdatum"):
                    version_pruefen(False, self.version, self.projekt)

    def test_changelog_muss_mit_aktueller_version_beginnen(self):
        self.schreiben("docs/index.html", self.doku.replace('<h2 data-anchor=', '<h2 data-anchor="alt">v0.1.0-alpha</h2><h2 data-anchor=', 1))
        with self.assertRaisesRegex(SystemExit, "neueste Changelog"):
            version_pruefen(projekt=self.projekt)

    def test_unvollstaendige_oder_fremde_jar_wird_abgewiesen(self):
        faelle = [
            ({"plugin.yml": b"version: v0.1.0-alpha"}, "Metadaten"),
            ({"de/lightrixx/plot/service/PlotDienst.class": None}, "Plugin-Klasse fehlt"),
            ({"org/bukkit/Server.class": b"fremd"}, "Server- oder Testbibliothek"),
            ({"de/lightrixx/plot/paper/SimplePlots.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x45"}, "Java-21"),
            ({"config/messages.yml": b"alt"}, "Veraltete Ressource"),
            ({"config/gui.yml": b"alt"}, "Veraltete Ressource"),
            ({"org/yaml/snakeyaml/Yaml.class": b"fremd"}, "Server- oder Testbibliothek"),
            ({"META-INF/LICENSE": None}, "konnte nicht geprüft"),
            ({"META-INF/maven/de.lightrixx/simpleplots-common/pom.properties": b"version=alt"}, "Maven-Version"),
        ]
        for aenderung, fehlermeldung in faelle:
            with self.subTest(aenderung=aenderung):
                self.jar(aenderung)
                with self.assertRaisesRegex(SystemExit, fehlermeldung):
                    version_pruefen(True, projekt=self.projekt)

    def test_doppelte_jar_eintraege_werden_abgewiesen(self):
        pfad = self.jar()
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(pfad, "a") as jar:
                jar.writestr("config.yml", b"claims: {}\n")
        with self.assertRaisesRegex(SystemExit, "Doppelte ZIP"):
            version_pruefen(True, projekt=self.projekt)

    def test_gueltige_dokumentationsnavigation(self):
        self.assertEqual((1, 1), doku_pruefen(self.projekt))

    def test_kaputte_navigation_und_offline_ressourcen_werden_abgewiesen(self):
        faelle = [
            (self.doku.replace('#/changelog/v0-2-0-alpha', '#/fehlt'), "Navigationsziel"),
            (self.doku.replace('#/changelog/v0-2-0-alpha', '#/changelog/fehlt'), "Navigationsziel"),
            (self.doku + self.doku, "doppelte Seiten-ID"),
            (self.doku.replace('</section>', '<h2 data-anchor="v0-2-0-alpha">Doppelt</h2></section>'), "Doppelter Anker"),
            (self.doku + '<script src="https://example.invalid/code.js"></script>', "Externe Ressource"),
            (self.doku + '<a href="fehlt.txt">Lizenz</a>', "Doku-Datei fehlt"),
            (self.doku + '<a href="../LICENSE">Lizenz</a>', "außerhalb von docs"),
            (self.doku + '<style>@import "https://example.invalid/font.css";</style>', "Schrift"),
        ]
        for inhalt, fehlermeldung in faelle:
            with self.subTest(fehlermeldung=fehlermeldung):
                self.schreiben("docs/index.html", inhalt)
                with self.assertRaisesRegex(SystemExit, fehlermeldung):
                    doku_pruefen(self.projekt)

    def test_release_enthaelt_genau_gepruefte_jar_und_pruefsumme(self):
        quelle = self.jar()
        self.schreiben("scripts/check-version.py", (SKRIPTE / "check-version.py").read_text(encoding="utf-8"))
        ziel = release_vorbereiten(self.projekt, self.version)
        self.assertEqual(quelle.read_bytes(), ziel.read_bytes())
        sha = hashlib.sha256(quelle.read_bytes()).hexdigest()
        self.assertEqual(f"{sha}  {ziel.name}\n".encode(), (self.projekt / "dist/SHA256SUMS.txt").read_bytes())
        text = (self.projekt / "dist/RELEASE_NOTES.md").read_text(encoding="utf-8")
        self.assertIn("- Build mit `verify`.", text)
        self.assertNotIn("<li>", text)

    def test_release_mit_falschem_tag_erzeugt_keine_artefakte(self):
        self.jar()
        (self.projekt / "scripts").mkdir()
        shutil.copyfile(SKRIPTE / "check-version.py", self.projekt / "scripts/check-version.py")
        with self.assertRaisesRegex(SystemExit, "Git-Tag"):
            release_vorbereiten(self.projekt, "v0.2.1-alpha")
        self.assertFalse((self.projekt / "dist").exists())


if __name__ == "__main__":
    unittest.main()
