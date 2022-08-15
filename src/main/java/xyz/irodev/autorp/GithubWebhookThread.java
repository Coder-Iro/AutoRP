package xyz.irodev.autorp;

import at.favre.lib.bytes.Bytes;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.MessageDigest;

public class GithubWebhookThread extends Thread {
    private final Key secretKey;
    private final int port;
    private final Runnable runnable;

    public GithubWebhookThread(String key, int port, Runnable runnable) {
        secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        this.port = port;
        this.runnable = runnable;
    }

    @Override
    public synchronized void start() {
        try {
            var server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/github", exchange -> {
                try (exchange) {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        var body = exchange.getRequestBody().readAllBytes();
                        var signature = exchange.getRequestHeaders().getFirst("X-hub-signature-256");

                        if (signature != null) {
                            var result = hexDigest(body);

                            if (MessageDigest.isEqual(signature.getBytes(), result.getBytes())) {
                                runnable.run();

                                exchange.sendResponseHeaders(204, -1);
                                return;
                            }
                        }

                        // Missing signature or invalid signature
                        exchange.sendResponseHeaders(403, 0);
                        return;
                    }

                    // Not a post request
                    exchange.sendResponseHeaders(400, 0);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            });

            server.start();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private String hexDigest(byte[] content) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);
        var bytes = mac.doFinal(content);

        return "sha256=" + Bytes.wrap(bytes).encodeHex();
    }
}
