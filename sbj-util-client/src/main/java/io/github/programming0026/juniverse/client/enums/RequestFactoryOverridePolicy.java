package io.github.programming0026.juniverse.client.enums;

import io.github.programming0026.juniverse.client.exception.ClientException;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.util.Locale;

/**
 * What the library does when a customizer ({@code RestClientCustomizer}, or those of a
 * {@code RestTemplateBuilder}) replaces the request factory of a client.
 *
 * The library's request factory is the one that carries the connection pool, the timeouts and the SSL of
 * {@code @RestHttpClient}. Customizers are applied after it so that tests can replace it
 * with a {@code MockRestServiceServer}, but a customizer meant for other clients of the application
 * (for example, a global one that sets timeouts) could also replace it by accident and leave the client
 * without its configuration. This policy decides which replacements are accepted. It is configured with the
 * {@value #PROPERTY} property.
 *
 * @author Java Universe
 */
public enum RequestFactoryOverridePolicy {

    /** Default: only Spring's test doubles are accepted; any other replacement is ignored. */
    AUTO,

    /** Any replacement is accepted (with a warning if it is not a test double). */
    ALLOW,

    /** A replacement is never accepted, not even that of a test double. */
    DENY;

    /** Name of the property that chooses the policy. */
    public static final String PROPERTY = "rest-http-client.request-factory-override";

    /** How many wrappers (for example {@code BufferingClientHttpRequestFactory}) are looked inside. */
    private static final int MAX_UNWRAP_DEPTH = 5;

    /** Package of the Spring request factory wrappers that can be looked inside. */
    private static final String SPRING_HTTP_CLIENT_PACKAGE = "org.springframework.http.client.";

    /**
     * Converts the property value.
     *
     * @param value the value ({@code auto}, {@code allow} or {@code deny}, case-insensitive); if it is
     *              null or empty {@link #AUTO} is used
     * @return the policy
     * @throws ClientException if the value is not valid
     */
    public static RequestFactoryOverridePolicy from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ClientException(PROPERTY + " must be auto, allow or deny but was '" + value + "'", e);
        }
    }

    /**
     * Indicates whether the policy accepts a customizer leaving this request factory in the client.
     *
     * @param replacement the request factory that would replace the library's one, or null if Spring's
     *                    default one is requested
     * @return true if it is accepted
     */
    public boolean accepts(ClientHttpRequestFactory replacement) {
        return switch (this) {
            case ALLOW -> true;
            case DENY -> false;
            default -> isTestDouble(replacement);
        };
    }

    /**
     * Indicates whether the request factory is a Spring test double: the one installed by
     * {@code MockRestServiceServer} (and Spring Boot's {@code MockServerRestClientCustomizer}), also
     * when wrapped in another one from {@code org.springframework.http.client} (for example, with
     * {@code bufferContent()}), or a Mockito mock.
     *
     * @param factory the request factory
     * @return true if it is a test double
     */
    public static boolean isTestDouble(ClientHttpRequestFactory factory) {
        ClientHttpRequestFactory current = factory;
        for (int depth = 0; current != null && depth < MAX_UNWRAP_DEPTH; depth++) {
            String name = current.getClass().getName();
            if (name.startsWith("org.springframework.test.") || name.startsWith("org.springframework.boot.test.")
                    || name.contains("$MockitoMock$")) {
                return true;
            }
            current = name.startsWith(SPRING_HTTP_CLIENT_PACKAGE) ? wrapped(current) : null;
        }
        return false;
    }

    /**
     * Gets the request factory wrapped by a Spring wrapper, using its public
     * {@code getDelegate()} method.
     *
     * @param wrapper the wrapper
     * @return the wrapped request factory, or null if it is not a Spring wrapper
     */
    private static ClientHttpRequestFactory wrapped(ClientHttpRequestFactory wrapper) {
        return wrapper instanceof AbstractClientHttpRequestFactoryWrapper spring ? spring.getDelegate() : null;
    }
}
