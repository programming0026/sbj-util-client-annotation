package com.juniverse.client.config;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Punto de extensión para configurar los clientes de la librería <b>uno por uno</b>: a diferencia de un
 * {@code RestClientCustomizer} de Spring Boot, que recibe el mismo trato para todos, este recibe el nombre del
 * cliente que se está creando y puede decidir qué hacer con cada uno.
 *
 * Los beans de este tipo se aplican a todos los clientes, después de los {@code RestClientCustomizer} (o del
 * {@code RestTemplateBuilder}) de la aplicación y antes de los interceptores de {@code @RestHttpClient}, en el
 * orden de {@code @Order}. Un cliente {@code REST_CLIENT} recibe el método con {@link RestClient.Builder} y uno
 * {@code REST_TEMPLATE}, el que recibe el {@link RestTemplate}. Ambos son opcionales.
 *
 * Reemplazar la request factory sigue sujeto a {@code rest-http-client.request-factory-override}: un reemplazo
 * que no sea un doble de pruebas se ignora por defecto, para no perder el pool, los timeouts y el SSL del cliente.
 *
 * <pre>{@code
 * @Bean
 * RestHttpClientCustomizer trace() {
 *     return new RestHttpClientCustomizer() {
 *         public void customize(String clientName, RestClient.Builder builder) {
 *             if (clientName.equals("PaymentsClient")) {
 *                 builder.defaultHeader("X-Origin", "mi-app");
 *             }
 *         }
 *     };
 * }
 * }</pre>
 */
public interface RestHttpClientCustomizer {

    /**
     * Personaliza el builder de un cliente {@code REST_CLIENT} antes de construirlo.
     *
     * @param clientName el nombre del cliente: el {@code name()} de {@code @RestHttpClient} o, si está vacío,
     *                   el nombre simple de la interfaz
     * @param builder el builder, que ya trae la URL base y la request factory del cliente
     */
    default void customize(String clientName, RestClient.Builder builder) {
    }

    /**
     * Personaliza el {@link RestTemplate} de un cliente {@code REST_TEMPLATE}.
     *
     * @param clientName el nombre del cliente: el {@code name()} de {@code @RestHttpClient} o, si está vacío,
     *                   el nombre simple de la interfaz
     * @param template el RestTemplate, que ya trae la request factory del cliente
     */
    default void customize(String clientName, RestTemplate template) {
    }
}
