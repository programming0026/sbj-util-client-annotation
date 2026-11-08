package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor de los clientes de DummyJSON: registra método, URL, estado y duración de cada petición.
 *
 * No es un bean de Spring: lo declara {@code @RestHttpClient(interceptors = ...)} y la librería lo crea.
 * Nunca registra headers ni cuerpos, para no filtrar tokens ni contraseñas.
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
