package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor of the DummyJSON clients: logs the method, URL, status and duration of each request.
 *
 * It is not a Spring bean: it is declared by {@code @RestHttpClient(interceptors = ...)} and the library creates it.
 * It never logs headers or bodies, so as not to leak tokens or passwords.
 *
 * @author Java Universe
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            LOG.info("DummyJSON {} {} -> {} ({} ms)", request.getMethod(), request.getURI(),
                    response.getStatusCode().value(), elapsedMillis(start));
            return response;
        } catch (IOException e) {
            LOG.warn("DummyJSON {} {} failed after {} ms: {}", request.getMethod(), request.getURI(),
                    elapsedMillis(start), e.toString());
            throw e;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
