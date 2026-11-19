package com.juniverse.client.example.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local HTTP server that acts as DummyJSON in the tests: it answers whatever is configured and stores the
 * received requests, to check what the library's clients actually send.
 *
 * @author Java Universe
 */
public final class FakeDummyJsonServer {

    /**
     * Received request.
     *
     * @param method HTTP method
     * @param path path (without query)
     * @param query query string as received, or null
     * @param headers headers, with lowercase names
     * @param body body as text
     */
    public record Received(String method, String path, String query, Map<String, String> headers, String body) {
    }

    private record Reply(int status, String contentType, byte[] body) {
    }

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Received> requests = new CopyOnWriteArrayList<>();

    /** Starts the server on a free port. */
    public FakeDummyJsonServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * Port it listens on.
     *
     * @return the port
     */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Base URL of the server.
     *
     * @return for example {@code http://127.0.0.1:54321}
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /**
     * Configures a JSON response.
     *
     * @param method HTTP method
     * @param path exact path
     * @param status status code
     * @param json JSON body
     */
    public void replyJson(String method, String path, int status, String json) {
        replies.put(key(method, path), new Reply(status, "application/json", json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Configures a binary response.
     *
     * @param method HTTP method
     * @param path exact path
     * @param contentType content type
     * @param body the bytes
     */
    public void replyBytes(String method, String path, String contentType, byte[] body) {
        replies.put(key(method, path), new Reply(200, contentType, body));
    }

    /**
     * Requests received since the last {@link #reset()}.
     *
     * @return the requests, in order
     */
    public List<Received> requests() {
        return List.copyOf(requests);
    }

    /**
     * Requests received for a method and a path.
     *
     * @param method HTTP method
     * @param path exact path
     * @return the matching requests
     */
    public List<Received> requests(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    /** Forgets the configured responses and the received requests. */
    public void reset() {
        replies.clear();
        requests.clear();
    }

    /** Stops the server. */
    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) ->
                headers.put(name.toLowerCase(Locale.ROOT), values.isEmpty() ? "" : values.get(0)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Received(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), headers,
                body));

        Reply reply = replies.getOrDefault(key(exchange.getRequestMethod(), path), new Reply(404, "application/json",
                "{\"message\":\"no route configured in the fake server\"}".getBytes(StandardCharsets.UTF_8)));
        exchange.getResponseHeaders().add("Content-Type", reply.contentType());
        exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? -1 : reply.body().length);
        if (reply.body().length > 0) {
            exchange.getResponseBody().write(reply.body());
        }
        exchange.close();
    }

    private static String key(String method, String path) {
        return method + " " + path;
    }
}
