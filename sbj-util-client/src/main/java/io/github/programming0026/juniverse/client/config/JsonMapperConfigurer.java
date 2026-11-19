package io.github.programming0026.juniverse.client.config;

import tools.jackson.databind.json.JsonMapper;

/**
 * Customizes the JSON (Jackson 3) of <b>one</b> client, with {@code @RestHttpClient(jsonConfig = ...)}.
 *
 * The client starts from the application's {@link JsonMapper} (with its {@code spring.jackson.*} properties) if
 * there is one, or from a new one with Jackson's default values if not, and only changes what this configurer
 * declares; the other clients and the application are not affected. Like the error handler or the
 * interceptors, if a bean of this type exists it is used; otherwise Spring creates the instance with
 * dependency injection.
 *
 * <pre>{@code
 * public class SnakeCaseJson implements JsonMapperConfigurer {
 *     public void configure(JsonMapper.Builder builder) {
 *         builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
 *                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 *     }
 * }
 * }</pre>
 *
 * @author Java Universe
 */
@FunctionalInterface
public interface JsonMapperConfigurer {

    /**
     * Modifies the builder of the client's {@link JsonMapper}.
     *
     * @param builder the builder, already carrying the application's configuration
     */
    void configure(JsonMapper.Builder builder);
}
