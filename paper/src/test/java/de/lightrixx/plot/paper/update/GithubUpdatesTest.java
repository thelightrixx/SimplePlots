package de.lightrixx.plot.paper.update;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class GithubUpdatesTest {
    private static final String VERSION = "v0.5.0-alpha";

    @Test
    void vergleichtVorabversionenNumerischUndBevorzugtStabileVersionen() {
        assertTrue(UpdateVersion.lesen("v0.5.0").compareTo(UpdateVersion.lesen(VERSION)) > 0);
        assertTrue(UpdateVersion.lesen("v0.5.0-alpha.10").compareTo(UpdateVersion.lesen("v0.5.0-alpha.2")) > 0);
        assertTrue(UpdateVersion.lesen("v0.6.0-alpha").compareTo(UpdateVersion.lesen("v0.5.9")) > 0);
        assertEquals(0, UpdateVersion.lesen(VERSION).compareTo(UpdateVersion.lesen(VERSION)));
        assertThrows(IllegalArgumentException.class, () -> UpdateVersion.lesen("0.5.0"));
        assertThrows(IllegalArgumentException.class, () -> UpdateVersion.lesen("v01.0.0"));
    }

    @Test
    void filtertKanaeleEntwuerfeUndAeltereVersionen() throws IOException {
        String json = "[" + release("v0.4.0-alpha", true, 20, "null") + "," + release(VERSION, true, 20, "null") + "]";
        assertEquals(VERSION, GithubUpdates.auswerten(json, "v0.4.0-alpha", true).orElseThrow().version());
        assertTrue(GithubUpdates.auswerten(json, "v0.4.0-alpha", false).isEmpty());
        assertTrue(GithubUpdates.auswerten(json, VERSION, true).isEmpty());
        assertTrue(GithubUpdates.auswerten(json.replace("\"draft\":false", "\"draft\":true"), "v0.1.0-alpha", true).isEmpty());
        String stabil = "[" + release("v0.4.0", false, 20, "null") + "," + release(VERSION, true, 20, "null") + "]";
        assertEquals("v0.4.0", GithubUpdates.auswerten(stabil, "v0.3.0-alpha", false).orElseThrow().version());
    }

    @Test
    void lehntFremdeDownloadAdressenUndDoppelteMetadatenAb() {
        String release = release(VERSION, true, 20, "null");
        assertThrows(IOException.class, () -> GithubUpdates.auswerten("[" + release.replace("https://github.com/", "https://fremd.example/") + "]", "v0.4.0-alpha", true));
        assertThrows(IOException.class, () -> GithubUpdates.auswerten("[" + release.replace("\"draft\":false", "\"draft\":false,\"draft\":true") + "]", "v0.4.0-alpha", true));
        assertThrows(IOException.class, () -> GithubUpdates.auswerten("{}", "v0.4.0-alpha", true));
        assertThrows(IOException.class, () -> GithubUpdates.auswerten("[" + release(VERSION, true, 80_000_000, "null") + "]", "v0.4.0-alpha", true));
    }

    @Test
    void prueftDownloadGroessePruefsummeUndPluginIdentitaet() throws IOException {
        byte[] jar = jar("SimplePlots", VERSION);
        String hash = GithubUpdates.sha256(jar);
        var release = GithubUpdates.auswerten("[" + release(VERSION, true, jar.length, "\"sha256:" + hash + "\"") + "]", "v0.4.0-alpha", true).orElseThrow();
        var github = new GithubUpdates((uri, maximum) -> uri.toString().endsWith(".txt")
                ? (hash + "  " + release.name() + "\r\n").getBytes(StandardCharsets.UTF_8) : jar);
        assertArrayEquals(jar, github.herunterladen(release));
        var kaputt = new GithubUpdates((uri, maximum) -> uri.toString().endsWith(".txt")
                ? ("0".repeat(64) + "  " + release.name()).getBytes(StandardCharsets.UTF_8) : jar);
        assertThrows(IOException.class, () -> kaputt.herunterladen(release));
        assertThrows(IOException.class, () -> GithubUpdates.pruefeJar(jar("AnderesPlugin", VERSION), VERSION));
        assertThrows(IOException.class, () -> GithubUpdates.pruefeJar(jar("SimplePlots", "v0.4.0-alpha"), VERSION));
        assertThrows(IOException.class, () -> GithubUpdates.pruefeJar(new byte[]{1, 2, 3}, VERSION));
    }

    @Test
    void verweigertFehlendeDoppelteUndWiderspruechlichePruefsummen() throws IOException {
        byte[] jar = jar("SimplePlots", VERSION);
        String hash = GithubUpdates.sha256(jar);
        var release = GithubUpdates.auswerten("[" + release(VERSION, true, jar.length, "\"sha256:" + "0".repeat(64) + "\"") + "]", "v0.4.0-alpha", true).orElseThrow();
        for (String sums : new String[]{"", "ungueltig", hash + "  " + release.name(),
                hash + "  " + release.name() + "\n" + hash + "  " + release.name()}) {
            var github = new GithubUpdates((uri, maximum) -> uri.toString().endsWith(".txt") ? sums.getBytes(StandardCharsets.UTF_8) : jar);
            assertThrows(IOException.class, () -> github.herunterladen(release));
        }
    }

    private static String release(String version, boolean vorab, int groesse, String digest) {
        String jar = "SimplePlots-" + version + ".jar";
        String url = "https://github.com/thelightrixx/SimplePlots/releases/download/" + version + "/";
        return "{\"tag_name\":\"" + version + "\",\"draft\":false,\"prerelease\":" + vorab + ",\"assets\":["
                + "{\"name\":\"" + jar + "\",\"size\":" + groesse + ",\"digest\":" + digest + ",\"browser_download_url\":\"" + url + jar + "\"},"
                + "{\"name\":\"SHA256SUMS.txt\",\"browser_download_url\":\"" + url + "SHA256SUMS.txt\"}]}";
    }

    private static byte[] jar(String name, String version) throws IOException {
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(("name: " + name + "\nversion: " + version + "\nmain: de.lightrixx.plot.paper.SimplePlots\napi-version: '26.1'\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("de/lightrixx/plot/paper/SimplePlots.class"));
            try (var klasse = GithubUpdates.class.getResourceAsStream("GithubUpdates.class")) {
                zip.write(klasse.readAllBytes());
            }
            zip.closeEntry();
            zip.finish();
            return bytes.toByteArray();
        }
    }
}
