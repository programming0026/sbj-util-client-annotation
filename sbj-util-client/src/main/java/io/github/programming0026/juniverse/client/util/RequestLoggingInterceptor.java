package io.github.programming0026.juniverse.client.util;

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
 * Logs the method, URL, status and duration of each HTTP request of a client ({@code logging = true}).
 * It never logs headers or bodies, and of the query it only shows the parameter names (their values
 * may carry tokens or personal data). It uses one logger per client, {@value #LOGGER_PREFIX}{@code <client>},
 * so its level can be raised or lowered from the logging configuration. One line is logged per attempt, so
 * retries are seen one by one.
 *
 * @author Java Universe
 */
public final class RequestLoggingInterceptor implements ClientHttpRequestInterceptor {

    /** Prefix of the logger name of each client. */
    public static final String LOGGER_PREFIX = Constants.BASE + ".http.";

    /** The client's logger. */
    private final Logger log;

    /**
     * Creates the interceptor of a client.
     *
     * @param clientName the client name
     */
    public RequestLoggingInterceptor(String clientName) {
        this.log = LoggerFactory.getLogger(LOGGER_PREFIX + clientName);
    }

    /**
     * Executes the request and logs its result.
     *
     * @param request the request
     * @param body the request body (not logged)
     * @param execution the rest of the chain
     * @return the response
     * @throws IOException if the request fails
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
     * Returns the URL without credentials or query values: {@code https://host/path?limit&skip}.
     *
     * @param uri the request URL
     * @return the text that is logged
     */
    public static String describe(URI uri) {
        String names = queryNames(uri.getRawQuery());
        return origin(uri) + Objects.toString(uri.getRawPath(), "") + (names.isEmpty() ? "" : "?" + names);
    }

    /**
     * Returns the scheme, host and port of the URL, without user or password.
     *
     * @param uri the URL
     * @return for example {@code https://host:8443}, or only what the URL has
     */
    private static String origin(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
        if (uri.getHost() == null) {
            return scheme;
        }
        return scheme + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    /**
     * Returns the names of the parameters of a query, without their values, joined with {@code &}.
     *
     * @param query the query without the {@code ?}, or {@code null}
     * @return the names, or an empty text if there are none
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
