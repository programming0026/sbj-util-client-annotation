package io.github.programming0026.juniverse.client.enums;

/**
 * HTTP implementation used by a client.
 *
 * @author Java Universe
 */
public enum ClientType {

    /** Uses Spring's {@code RestClient} (the default). */
    REST_CLIENT,

    /** Uses Spring's {@code RestTemplate}. */
    REST_TEMPLATE,

}
