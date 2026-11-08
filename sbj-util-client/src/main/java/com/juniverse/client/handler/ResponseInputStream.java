package com.juniverse.client.handler;

import org.springframework.http.client.ClientHttpResponse;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cuerpo de una respuesta que se lee progresivamente (el retorno {@code InputStream} de un cliente).
 *
 * La respuesta sigue abierta, y su conexión del pool ocupada, hasta que se llama a {@link #close()}: quien
 * recibe el flujo es responsable de cerrarlo (con {@code try-with-resources}). Al cerrarlo se libera la
 * conexión; si no se leyó todo el cuerpo, HttpClient descarta el resto para poder reutilizarla.
 */
final class ResponseInputStream extends FilterInputStream {

    /** La respuesta a la que pertenece el cuerpo; cerrarla libera la conexión. */
    private final ClientHttpResponse response;

    /** Si ya se cerró: cerrar dos veces es inofensivo. */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Envuelve el cuerpo de una respuesta.
     *
     * @param response la respuesta, que debe seguir abierta
     * @throws IOException si no se puede obtener el cuerpo
     */
    ResponseInputStream(ClientHttpResponse response) throws IOException {
        super(bodyOf(response));
        this.response = response;
    }

    private static InputStream bodyOf(ClientHttpResponse response) throws IOException {
        InputStream body = response.getBody();
        return body != null ? body : InputStream.nullInputStream();
    }

    /**
     * Cierra el cuerpo y la respuesta, liberando la conexión del pool.
     *
     * @throws IOException si falla el cierre del cuerpo
     */
    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            try {
                response.close();
            } finally {
                super.close();
            }
        }
    }
}
