package de.lightrixx.plot.paper.update;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

public final class GithubUpdates {
    private static final String REPO = "thelightrixx/SimplePlots";
    private static final long MAX_JAR = 64L * 1024 * 1024;
    private final Abruf abruf;
    private final String minecraft;

    public GithubUpdates(String minecraft) { this(GithubUpdates::http, minecraft); }
    GithubUpdates(Abruf abruf) { this(abruf, "26.1.2"); }
    GithubUpdates(Abruf abruf, String minecraft) { this.abruf = abruf; this.minecraft = minecraft; }

    public Optional<Release> pruefen(String installiert, boolean vorab) throws IOException {
        return pruefen(installiert, vorab ? "alpha" : "stable");
    }

    public Optional<Release> pruefen(String installiert, String kanal) throws IOException {
        String json = new String(abruf.laden(URI.create("https://api.github.com/repos/" + REPO + "/releases?per_page=30"),
                2 * 1024 * 1024), StandardCharsets.UTF_8);
        return auswerten(json, installiert, kanal);
    }

    static Optional<Release> auswerten(String json, String installiert, boolean vorab) throws IOException {
        return auswerten(json, installiert, vorab ? "alpha" : "stable");
    }

    static Optional<Release> auswerten(String json, String installiert, String kanal) throws IOException {
        if (!List.of("stable", "beta", "alpha").contains(kanal)) throw new IOException("Ungültiger Update-Kanal.");
        boolean vorab = !kanal.equals("stable");
        var optionen = new LoaderOptions();
        optionen.setAllowDuplicateKeys(false);
        optionen.setCodePointLimit(2 * 1024 * 1024);
        try {
            Object daten = new Yaml(new SafeConstructor(optionen)).load(json);
            if (!(daten instanceof List<?> releases)) throw new IOException("GitHub lieferte keine Release-Liste.");
            UpdateVersion aktuelle = UpdateVersion.lesen(installiert);
            Release neueste = null;
            for (Object eintrag : releases) {
                if (!(eintrag instanceof Map<?, ?> release)) throw new IOException("Ungültiger GitHub-Release.");
                if (!Boolean.FALSE.equals(release.get("draft")) || (!vorab && !Boolean.FALSE.equals(release.get("prerelease")))) continue;
                String version = String.valueOf(release.get("tag_name"));
                UpdateVersion vergleich;
                try {
                    vergleich = UpdateVersion.lesen(version);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (vergleich.compareTo(aktuelle) <= 0 || (!vorab && !vergleich.vorab().isEmpty())) continue;
                if (kanal.equals("beta") && !vergleich.vorab().isEmpty()
                        && !List.of("beta", "rc").contains(vergleich.vorab().getFirst())) continue;
                if (!(release.get("assets") instanceof List<?> assets)) throw new IOException("Release-Dateien fehlen.");
                String name = "SimplePlots-" + version + ".jar";
                Map<?, ?> jar = asset(assets, name), sums = asset(assets, "SHA256SUMS.txt");
                if (jar == null || sums == null) continue;
                if (!(jar.get("size") instanceof Number groesse) || groesse.longValue() < 1 || groesse.longValue() > MAX_JAR) {
                    throw new IOException("Unzulässige JAR-Größe.");
                }
                var kandidat = new Release(version, name, download(version, name, jar),
                        download(version, "SHA256SUMS.txt", sums), groesse.longValue(), String.valueOf(jar.get("digest")));
                if (neueste == null || vergleich.compareTo(UpdateVersion.lesen(neueste.version())) > 0) neueste = kandidat;
            }
            return Optional.ofNullable(neueste);
        } catch (IllegalArgumentException | org.yaml.snakeyaml.error.YAMLException e) {
            throw new IOException("Ungültige Update-Metadaten.", e);
        }
    }

    private static Map<?, ?> asset(List<?> assets, String name) throws IOException {
        Map<?, ?> treffer = null;
        for (Object wert : assets) {
            if (wert instanceof Map<?, ?> asset && name.equals(asset.get("name"))) {
                if (treffer != null) throw new IOException("Doppelte Release-Datei: " + name);
                treffer = asset;
            }
        }
        return treffer;
    }

    private static URI download(String version, String name, Map<?, ?> asset) throws IOException {
        URI erwartet = URI.create("https://github.com/" + REPO + "/releases/download/" + version + "/" + name);
        if (!erwartet.toString().equals(asset.get("browser_download_url"))) throw new IOException("Unerwartete Download-Adresse.");
        return erwartet;
    }

    public byte[] herunterladen(Release release) throws IOException {
        String sums = new String(abruf.laden(release.summen(), 64 * 1024), StandardCharsets.UTF_8);
        String erwartet = null;
        for (String zeile : sums.lines().toList()) {
            if (zeile.isBlank()) continue;
            var match = java.util.regex.Pattern.compile("([A-Fa-f0-9]{64}) [ *](.+)").matcher(zeile);
            if (!match.matches()) throw new IOException("Ungültige SHA256SUMS.txt.");
            if (match.group(2).equals(release.name())) {
                if (erwartet != null) throw new IOException("Doppelte Prüfsumme.");
                erwartet = match.group(1).toLowerCase(java.util.Locale.ROOT);
            }
        }
        if (erwartet == null) throw new IOException("JAR-Prüfsumme fehlt.");
        byte[] jar = abruf.laden(release.jar(), MAX_JAR);
        if (jar.length != release.groesse() || !erwartet.equals(sha256(jar))
                || (!release.digest().equals("null") && !release.digest().equals("sha256:" + erwartet))) {
            throw new IOException("Die Update-JAR stimmt nicht mit der veröffentlichten SHA-256-Prüfsumme überein.");
        }
        pruefeJar(jar, release.version(), minecraft);
        return jar;
    }

    static void pruefeJar(byte[] bytes, String version) throws IOException {
        pruefeJar(bytes, version, "26.1.2");
    }

    private static void pruefeJar(byte[] bytes, String version, String minecraft) throws IOException {
        boolean ende = false;
        for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
            if (bytes[i] == 0x50 && bytes[i + 1] == 0x4b && bytes[i + 2] == 0x05 && bytes[i + 3] == 0x06
                    && i + 22 + ((bytes[i + 20] & 255) | ((bytes[i + 21] & 255) << 8)) == bytes.length) {
                ende = true;
                break;
            }
        }
        if (!ende) throw new IOException("Unvollständiges JAR-Archiv.");
        boolean gefunden = false;
        boolean hauptklasse = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry eintrag;
            int anzahl = 0;
            long summe = 0;
            while ((eintrag = zip.getNextEntry()) != null) {
                if (++anzahl > 10000 || eintrag.getName().startsWith("/") || eintrag.getName().contains("..")) {
                    throw new IOException("Unzulässiger JAR-Inhalt.");
                }
                byte[] inhalt = zip.readNBytes(8 * 1024 * 1024 + 1);
                summe += inhalt.length;
                if (inhalt.length > 8 * 1024 * 1024 || summe > MAX_JAR * 4) throw new IOException("JAR-Inhalt zu groß.");
                if (eintrag.getName().equals("plugin.yml")) {
                    if (gefunden) throw new IOException("Doppelte Plugin-Metadaten.");
                    var opt = new LoaderOptions(); opt.setAllowDuplicateKeys(false);
                    Object daten = new Yaml(new SafeConstructor(opt)).load(new String(inhalt, StandardCharsets.UTF_8));
                    if (!(daten instanceof Map<?, ?> meta) || !"SimplePlots".equals(meta.get("name"))
                            || !version.equals(meta.get("version")) || !"de.lightrixx.plot.paper.SimplePlots".equals(meta.get("main"))) {
                        throw new IOException("Die Update-JAR ist keine passende SimplePlots-Version.");
                    }
                    if (!apiPasst(String.valueOf(meta.get("api-version")), minecraft)) {
                        throw new IOException("Das Update benötigt eine neuere Paper-Version.");
                    }
                    gefunden = true;
                }
                if (eintrag.getName().equals("de/lightrixx/plot/paper/SimplePlots.class")) {
                    if (hauptklasse || inhalt.length < 8 || java.nio.ByteBuffer.wrap(inhalt).getInt() != 0xCAFEBABE
                            || Short.toUnsignedInt(java.nio.ByteBuffer.wrap(inhalt).getShort(6)) > Runtime.version().feature() + 44) {
                        throw new IOException("Das Update enthält keine ausführbare Hauptklasse für diese Java-Version.");
                    }
                    hauptklasse = true;
                }
            }
        } catch (org.yaml.snakeyaml.error.YAMLException e) {
            throw new IOException("Ungültige Plugin-Metadaten in der Update-JAR.", e);
        }
        if (!gefunden || !hauptklasse) throw new IOException("Plugin-Metadaten oder Hauptklasse fehlen in der Update-JAR.");
    }

    private static boolean apiPasst(String verlangt, String vorhanden) {
        if (!verlangt.matches("[0-9]+(?:\\.[0-9]+){1,2}") || !vorhanden.matches("[0-9]+(?:\\.[0-9]+){1,2}")) return false;
        String[] ziel = verlangt.split("\\."), server = vorhanden.split("\\.");
        for (int i = 0; i < Math.max(ziel.length, server.length); i++) {
            try {
                int vergleich = Integer.compare(i < ziel.length ? Integer.parseInt(ziel[i]) : 0,
                        i < server.length ? Integer.parseInt(server[i]) : 0);
                if (vergleich != 0) return vergleich < 0;
            } catch (NumberFormatException e) { return false; }
        }
        return true;
    }

    public static String sha256(byte[] daten) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(daten));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in der Java-Laufzeit.", e);
        }
    }

    private static byte[] http(URI adresse, long maximum) throws IOException {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            for (int i = 0; i < 5; i++) {
                String host = adresse.getHost();
                if (!"https".equals(adresse.getScheme()) || adresse.getUserInfo() != null || adresse.getFragment() != null
                        || (adresse.getPort() != -1 && adresse.getPort() != 443)
                        || !("api.github.com".equals(host) || "github.com".equals(host)
                        || (host != null && host.endsWith(".githubusercontent.com")))) {
                    throw new IOException("Update-Weiterleitung außerhalb von GitHub.");
                }
                var request = HttpRequest.newBuilder(adresse).timeout(Duration.ofSeconds(40))
                        .header("User-Agent", "SimplePlots-Updater").header("Accept", "application/vnd.github+json").GET().build();
                var future = client.sendAsync(request, info -> new BegrenzterInhalt(maximum));
                HttpResponse<byte[]> antwort;
                try {
                    antwort = future.get(45, TimeUnit.SECONDS);
                } catch (Exception e) {
                    future.cancel(true);
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new IOException("Update-Abruf unterbrochen oder Zeitlimit überschritten.", e);
                }
                if (antwort.statusCode() >= 300 && antwort.statusCode() < 400) {
                    String ziel = antwort.headers().firstValue("Location").orElseThrow(() -> new IOException("Weiterleitungsziel fehlt."));
                    adresse = adresse.resolve(ziel);
                } else {
                    if (antwort.statusCode() != 200) throw new IOException("GitHub antwortete mit HTTP " + antwort.statusCode());
                    return antwort.body();
                }
            }
            throw new IOException("Zu viele Update-Weiterleitungen.");
        }
    }

    private static final class BegrenzterInhalt implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> ziel = HttpResponse.BodySubscribers.ofByteArray();
        private final long maximum;
        private Flow.Subscription subscription;
        private long groesse;
        BegrenzterInhalt(long maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return ziel.getBody(); }
        @Override public void onSubscribe(Flow.Subscription wert) { subscription = wert; ziel.onSubscribe(wert); }
        @Override public void onNext(List<ByteBuffer> teile) {
            for (ByteBuffer teil : teile) groesse += teil.remaining();
            if (groesse > maximum) {
                subscription.cancel();
                ziel.onError(new IOException("Update-Download überschreitet die Größenbegrenzung."));
            } else ziel.onNext(teile);
        }
        @Override public void onError(Throwable fehler) { ziel.onError(fehler); }
        @Override public void onComplete() { ziel.onComplete(); }
    }

    public record Release(String version, String name, URI jar, URI summen, long groesse, String digest) { }
    @FunctionalInterface interface Abruf { byte[] laden(URI adresse, long maximum) throws IOException; }
}
