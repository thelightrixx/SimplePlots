package de.lightrixx.plot.paper.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GithubPlattformTest {
    @Test
    void waehltDieZurLaufendenServerreihePassendeJar() throws Exception {
        String base = "https://github.com/thelightrixx/SimplePlots/releases/download/v0.9.0/";
        String json = "[{\"draft\":false,\"prerelease\":false,\"tag_name\":\"v0.9.0\",\"assets\":["
                + "{\"name\":\"SimplePlots-v0.9.0-paper.jar\",\"size\":20,\"browser_download_url\":\"" + base + "SimplePlots-v0.9.0-paper.jar\"},"
                + "{\"name\":\"SimplePlots-v0.9.0-paper-1.21.jar\",\"size\":20,\"browser_download_url\":\"" + base + "SimplePlots-v0.9.0-paper-1.21.jar\"},"
                + "{\"name\":\"SHA256SUMS.txt\",\"browser_download_url\":\"" + base + "SHA256SUMS.txt\"}]}]";
        assertEquals("SimplePlots-v0.9.0-paper.jar",
                GithubUpdates.auswerten(json, "v0.8.0-beta", "stable", "26.1.2").orElseThrow().name());
        assertEquals("SimplePlots-v0.9.0-paper-1.21.jar",
                GithubUpdates.auswerten(json, "v0.8.0-beta", "stable", "1.21.8").orElseThrow().name());
        String onlyModern = json.replace("SimplePlots-v0.9.0-paper-1.21.jar", "unrelated.jar");
        assertTrue(GithubUpdates.auswerten(onlyModern, "v0.8.0-beta", "stable", "1.21.8").isEmpty());
    }
}
