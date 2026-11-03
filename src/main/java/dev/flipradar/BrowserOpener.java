package dev.flipradar;

import java.io.IOException;

final class BrowserOpener {
    private BrowserOpener() {}

    static void open(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException e) {
            FlipRadarClient.LOG.warn("Не удалось открыть браузер: {}", e.getMessage());
        }
    }
}
