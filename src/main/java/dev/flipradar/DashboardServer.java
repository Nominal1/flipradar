package dev.flipradar;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Минимальный HTTP-сервер только на 127.0.0.1 (без модуля jdk.httpserver — на случай урезанной Java в лаунчере).
 */
public final class DashboardServer {
    private final DataService data;
    private ServerSocket socket;
    private byte[] html = new byte[0];
    private volatile int port = -1;

    public DashboardServer(DataService data) {
        this.data = data;
    }

    public int port() {
        return port;
    }

    public String url() {
        return "http://127.0.0.1:" + port + "/";
    }

    public boolean start() {
        try (InputStream in = DashboardServer.class.getResourceAsStream("/flipradar/dashboard.html")) {
            if (in != null) html = in.readAllBytes();
        } catch (IOException ignored) {
        }
        for (int p = 8765; p < 8776; p++) {
            try {
                socket = new ServerSocket(p, 20, InetAddress.getLoopbackAddress());
                port = p;
                break;
            } catch (IOException ignored) {
            }
        }
        if (socket == null) return false;
        Thread t = new Thread(this::acceptLoop, "FlipRadar-http");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                Socket s = socket.accept();
                Thread.ofVirtual().start(() -> handle(s));
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handle(Socket s) {
        try (s) {
            s.setSoTimeout(5000);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
            String line = r.readLine();
            if (line == null) return;
            String host = "";
            String h;
            while ((h = r.readLine()) != null && !h.isEmpty()) {
                if (h.regionMatches(true, 0, "Host:", 0, 5)) host = h.substring(5).trim();
            }
            // Защита от DNS-rebinding: принимаем только локальный Host.
            if (!(host.startsWith("127.0.0.1") || host.startsWith("localhost"))) {
                respond(s, 403, "text/plain; charset=utf-8", "forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String[] parts = line.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);

            switch (path) {
                case "/" -> respond(s, 200, "text/html; charset=utf-8", html);
                case "/api/bazaar" -> respond(s, 200, "application/json; charset=utf-8", bytes(data.bazaarJson));
                case "/api/auctions" -> respond(s, 200, "application/json; charset=utf-8", bytes(data.auctionsJson));
                case "/api/status" -> respond(s, 200, "application/json; charset=utf-8", bytes(data.statusJson()));
                default -> respond(s, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(Socket s, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 403 ? "Forbidden" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = s.getOutputStream();
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
