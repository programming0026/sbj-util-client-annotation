package io.github.programming0026.juniverse.client.enums;

/**
 * Implementación HTTP que usa un cliente.
 *
 * @author Java Universe
 */
public enum ClientType {

    /** Usa {@code RestClient} de Spring (por defecto). */
    REST_CLIENT,

    /** Usa {@code RestTemplate} de Spring. */
    REST_TEMPLATE,

}
