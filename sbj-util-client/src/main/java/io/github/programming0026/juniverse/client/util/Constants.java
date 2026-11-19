package io.github.programming0026.juniverse.client.util;

/**
 * The library's root package, written as text in a single place.
 * The two files in {@code META-INF/spring} (auto-configuration and AOT) do not accept constants and carry the
 * full name of their classes; the same test checks that they still point to classes that exist.
 *
 * @author Java Universe
 */
public final class Constants {

    /** The library's root package; it is a compile-time constant, so it can be used in annotations. */
    public static final String BASE = "io.github.programming0026.juniverse.client";

    /**
     * Utility class: it is not instantiated.
     */
    private Constants() {
    }
}
