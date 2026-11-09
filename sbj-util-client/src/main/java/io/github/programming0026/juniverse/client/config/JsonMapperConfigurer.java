package io.github.programming0026.juniverse.client.config;

import tools.jackson.databind.json.JsonMapper;

/**
 * Personaliza el JSON (Jackson 3) de <b>un</b> cliente, con {@code @RestHttpClient(jsonConfig = ...)}.
 *
 * El cliente parte del {@link JsonMapper} de la aplicación (con sus propiedades {@code spring.jackson.*}) si hay
 * uno, o de uno nuevo con los valores por defecto de Jackson si no, y solo cambia lo que este configurador
 * declare; el resto de clientes y la aplicación no se ven afectados. Igual que el manejador de errores o los
 * interceptores, si existe un bean de este tipo se usa; si no, Spring crea la instancia con inyección de
 * dependencias.
 *
 * <pre>{@code
 * public class SnakeCaseJson implements JsonMapperConfigurer {
 *     public void configure(JsonMapper.Builder builder) {
 *         builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
 *                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface JsonMapperConfigurer {

    /**
     * Modifica el builder del {@link JsonMapper} del cliente.
     *
     * @param builder el builder, ya con la configuración de la aplicación
     */
    void configure(JsonMapper.Builder builder);
}
