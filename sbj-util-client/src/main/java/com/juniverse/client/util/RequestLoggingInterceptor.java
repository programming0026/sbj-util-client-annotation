package com.juniverse.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Registra método, URL, estado y duración de cada petición HTTP de un cliente ({@code logging = true}).
 * Nunca registra headers ni cuerpos, y de la query solo muestra los nombres de los parámetros (sus valores
 * pueden llevar tokens o datos personales). Usa un logger por cliente, {@value #LOGGER_PREFIX}{@code <cliente>},
 * para poder subir o bajar su nivel desde la configuración de logging. Se registra una línea por intento, así que
 * los reintentos se ven uno a uno.
 */
public final class RequestLoggingInterceptor implements ClientHttpRequestInterceptor {

    /** Prefijo del nombre del logger de cada cliente. */
    public static final String LOGGER_PREFIX = "com.juniverse.client.http.";

    /** El logger del cliente. */
    private final Logger log;

    /**
     * Crea el interceptor de un cliente.
     *
     * @param clientName el nombre del cliente
     */
    public RequestLoggingInterceptor(String clientName) {
        this.log = LoggerFactory.getLogger(LOGGER_PREFIX + clientName);
    }

    /**
     * Ejecuta la petición y registra su resultado.
     *
     * @param request la petición
     * @param body el cuerpo de la petición (no se registra)
     * @param execution el resto de la cadena
     * @return la respuesta
     * @throws IOException si falla la petición
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            if (log.isInfoEnabled()) {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), describe(request.getURI()),
                        response.getStatusCode().value(), elapsedMillis(start));
            }
            return response;
        } catch (IOException | RuntimeException e) {
            if (log.isWarnEnabled()) {
                log.warn("{} {} failed after {} ms: {}", request.getMethod(), describe(request.getURI()),
                        elapsedMillis(start), e.toString());
            }
            throw e;
        }
    }

    /**
     * Devuelve la URL sin credenciales ni valores de la query: {@code https://host/ruta?limit&skip}.
     *
     * @param uri la URL de la petición
     * @return el texto que se registra
     */
    public static String describe(URI uri) {
        String names = queryNames(uri.getRawQuery());
        return origin(uri) + Objects.toString(uri.getRawPath(), "") + (names.isEmpty() ? "" : "?" + names);
    }

    /**
     * Devuelve el protocolo, el host y el puerto de la URL, sin usuario ni contraseña.
     *
     * @param uri la URL
     * @return por ejemplo {@code https://host:8443}, o solo lo que la URL tenga
     */
    private static String origin(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
        if (uri.getHost() == null) {
            return scheme;
        }
        return scheme + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    /**
     * Devuelve los nombres de los parámetros de una query, sin sus valores, unidos con {@code &}.
     *
     * @param query la query sin él {@code ?}, o {@code null}
     * @return los nombres, o un texto vacío si no hay
     */
    private static String queryNames(String query) {
        if (query == null) {
            return "";
        }
        return Arrays.stream(query.split("&"))
                .filter(pair -> !pair.isEmpty())
                .map(pair -> pair.indexOf('=') < 0 ? pair : pair.substring(0, pair.indexOf('=')))
                .collect(Collectors.joining("&"));
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
