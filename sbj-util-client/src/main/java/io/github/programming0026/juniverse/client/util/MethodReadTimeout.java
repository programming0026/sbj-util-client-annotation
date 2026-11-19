package io.github.programming0026.juniverse.client.util;

import java.util.OptionalLong;

/**
 * Carries the {@code readTimeout} of a method ({@code @MethodConfig}) to the Apache HttpClient request
 * factory, which reads it when creating the request. It works with {@code RestClient} and {@code RestTemplate}
 * because the request is created in the same thread that initiated it.
 *
 * It is for internal use by the library: the handler sets it right before making the request and clears it when
 * done.
 *
 * @author Java Universe
 */
public final class MethodReadTimeout {

    /** The timeout in milliseconds of the thread's current request, or null if the client's one is used. */
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private MethodReadTimeout() {
    }

    /**
     * Sets the read timeout of the request this thread is about to make.
     *
     * @param millis the timeout in milliseconds
     */
    public static void set(long millis) {
        CURRENT.set(millis);
    }

    /** Removes the thread's read timeout, so the following requests use the client's one. */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Gets the read timeout set for this thread.
     *
     * @return the timeout in milliseconds, or empty if there is none
     */
    public static OptionalLong current() {
        Long value = CURRENT.get();
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }
}
