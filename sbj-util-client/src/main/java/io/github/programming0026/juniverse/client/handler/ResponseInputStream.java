package io.github.programming0026.juniverse.client.handler;

import org.springframework.http.client.ClientHttpResponse;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Body of a response that is read progressively (the {@code InputStream} return type of a client).
 * The response stays open, and its pool connection busy, until {@link #close()} is called: whoever
 * receives the stream is responsible for closing it (with {@code try-with-resources}). Closing it releases the
 * connection; if the body was not read, HttpClient discards the rest so it can be reused.
 *
 * @author Java Universe
 */
public final class ResponseInputStream extends FilterInputStream {

    /** The response the body belongs to; closing it releases the connection. */
    private final ClientHttpResponse response;

    /** Whether it has already been closed: closing twice is harmless. */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Wraps the body of a response.
     *
     * @param response the response, which must still be open
     * @throws IOException if the body cannot be obtained
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
     * Reads up to {@code len} bytes directly from the response body, bypassing byte-by-byte reading.
     *
     * @param buffer the destination
     * @param offset the start position in the destination
     * @param length the maximum number of bytes to read
     * @return the bytes read, or -1 at the end
     * @throws IOException if the read fails
     */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return in.read(buffer, offset, length);
    }

    /**
     * Closes the body and the response, releasing the pool connection.
     *
     * @throws IOException if closing the body fails
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
