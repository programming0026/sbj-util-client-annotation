package io.github.programming0026.juniverse.client.config;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Extension point to configure the library's clients <b>one by one</b>: unlike a Spring Boot
 * {@code RestClientCustomizer}, which gets the same treatment for all of them, this one receives the name of
 * the client being created and can decide what to do with each one.
 *
 * Beans of this type are applied to all the clients, after the application's {@code RestClientCustomizer}
 * (or {@code RestTemplateBuilder}) and before the {@code @RestHttpClient} interceptors, in
 * {@code @Order} order. A {@code REST_CLIENT} client receives the method with {@link RestClient.Builder} and a
 * {@code REST_TEMPLATE} one, the method that receives the {@link RestTemplate}. Both are optional.
 *
 * Replacing the request factory remains subject to {@code rest-http-client.request-factory-override}: a
 * replacement that is not a test double is ignored by default, so the client's pool, timeouts and SSL are not
 * lost.
 *
 * <pre>{@code
 * @Bean
 * RestHttpClientCustomizer trace() {
 *     return new RestHttpClientCustomizer() {
 *         public void customize(String clientName, RestClient.Builder builder) {
 *             if (clientName.equals("PaymentsClient")) {
 *                 builder.defaultHeader("X-Origin", "my-app");
 *             }
 *         }
 *     };
 * }
 * }</pre>
 *
 * @author Java Universe
 */
public interface RestHttpClientCustomizer {

    /**
     * Customizes the builder of a {@code REST_CLIENT} client before building it.
     *
     * @param clientName the client name: the {@code name()} of {@code @RestHttpClient} or, if empty,
     *                   the simple name of the interface
     * @param builder the builder, which already carries the base URL and the client's request factory
     */
    default void customize(String clientName, RestClient.Builder builder) {
    }

    /**
     * Customizes the {@link RestTemplate} of a {@code REST_TEMPLATE} client.
     *
     * @param clientName the client name: the {@code name()} of {@code @RestHttpClient} or, if empty,
     *                   the simple name of the interface
     * @param template the RestTemplate, which already carries the client's request factory
     */
    default void customize(String clientName, RestTemplate template) {
    }
}
