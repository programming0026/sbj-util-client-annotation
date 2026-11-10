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
 * Servidor HTTP local que hace de DummyJSON en los tests: responde lo que se le configure y guarda las
 * peticiones recibidas, para comprobar qué envían realmente los clientes de la librería.
 *
 * @author Java Universe
 */
public final class FakeDummyJsonServer {

    /**
     * Petición recibida.
     *
     * @param method método HTTP
     * @param path ruta (sin query)
     * @param query query string tal como llegó, o null
     * @param headers headers, con los nombres en minúsculas
     * @param body cuerpo como texto
     */
    public record Received(String method, String path, String query, Map<String, String> headers, String body) {
    }

    private record Reply(int status, String contentType, byte[] body) {
    }

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Received> requests = new CopyOnWriteArrayList<>();

    /** Arranca el servidor en un puerto libre. */
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
     * Puerto en el que escucha.
     *
     * @return el puerto
     */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * URL base del servidor.
     *
     * @return por ejemplo {@code http://127.0.0.1:54321}
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /**
     * Configura una respuesta JSON.
     *
     * @param method método HTTP
     * @param path ruta exacta
     * @param status código de estado
     * @param json cuerpo JSON
     */
    public void replyJson(String method, String path, int status, String json) {
        replies.put(key(method, path), new Reply(status, "application/json", json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Configura una respuesta binaria.
     *
     * @param method método HTTP
     * @param path ruta exacta
     * @param contentType tipo de contenido
     * @param body los bytes
     */
    public void replyBytes(String method, String path, String contentType, byte[] body) {
        replies.put(key(method, path), new Reply(200, contentType, body));
    }

    /**
     * Peticiones recibidas desde el último {@link #reset()}.
     *
     * @return las peticiones, en orden
     */
    public List<Received> requests() {
        return List.copyOf(requests);
    }

    /**
     * Peticiones recibidas para un método y una ruta.
     *
     * @param method método HTTP
     * @param path ruta exacta
     * @return las peticiones que coinciden
     */
    public List<Received> requests(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    /** Olvida las respuestas configuradas y las peticiones recibidas. */
    public void reset() {
        replies.clear();
        requests.clear();
    }

    /** Detiene el servidor. */
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
