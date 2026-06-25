package org.worldstatus.prometheus;

import com.sun.net.httpserver.HttpServer;
import org.worldstatus.WorldStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class Server {

    private final WorldStatus plugin;
    private HttpServer server;
    private Metrics metrics;

    public Server(WorldStatus plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("prometheus.enabled", false)) {
            return;
        }

        String bindIp = plugin.getConfig().getString("prometheus.bind-ip", "0.0.0.0");
        int    port   = plugin.getConfig().getInt("prometheus.port", 4233);

        try {
            server  = HttpServer.create(new InetSocketAddress(bindIp, port), 0);
            server.setExecutor(Executors.newFixedThreadPool(2));

            metrics = new Metrics(plugin);

            server.createContext("/metrics", exchange -> {
                try {
                    String response = metrics.generateMetrics();
                    byte[] bytes    = response.getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);

                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error generating Prometheus metrics", e);
                    exchange.sendResponseHeaders(500, -1);
                } finally {
                    exchange.close();
                }
            });

            server.start();
            plugin.getLogger().info("[WorldStatus] Prometheus metrics server started on " + bindIp + ":" + port);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[WorldStatus] Failed to start Prometheus metrics server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("[WorldStatus] Prometheus metrics server stopped.");
        }
    }
}
