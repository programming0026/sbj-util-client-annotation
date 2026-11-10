package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ajustes de un método del cliente que reemplazan los del {@link RestHttpClient} solo para ese método; por
 * ejemplo, dar más tiempo a una descarga sin aflojar los límites del resto de las llamadas. Lo que no se indica
 * se hereda del cliente. El circuit breaker y el rate limiter siguen siendo del cliente completo.
 *
 * <pre>{@code
 * @GetMapping("/reports/{id}")
 * @MethodConfig(readTimeout = 120_000, maxRetries = 1, timeLimiterTimeout = 0)
 * InputStream report(@PathVariable String id);
 * }</pre>
 *
 * Los valores se validan al crear el cliente.
 *
 * @author Java Universe
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodConfig {

    /**
     * Tiempo máximo de espera de la respuesta, en milisegundos, para este método (reemplaza a
     * {@link RestHttpClient#readTimeout()}). Con un retorno {@code InputStream} rige también cada lectura del
     * cuerpo. {@code -1} (por defecto) hereda el del cliente; si se indica debe ser mayor que cero.
     *
     * @return el read timeout en milisegundos, o {@code -1} para heredarlo
     */
    long readTimeout() default -1;

    /**
     * Número total de intentos para este método, el primero incluido (reemplaza a
     * {@link RetryConfig#maxRetries()}; {@code 1} significa sin reintentos). El resto de la configuración de
     * reintentos (espera, backoff, {@code retryNonIdempotent}) se hereda del cliente. {@code -1} (por defecto)
     * hereda el del cliente; si se indica debe ser al menos {@code 1}.
     *
     * @return el total de intentos, o {@code -1} para heredarlo
     */
    int maxRetries() default -1;

    /**
     * Tiempo máximo de cada intento, en milisegundos, para este método (reemplaza a
     * {@link TimeLimiterConfig#timeout()}). Con un valor mayor que cero el método tiene límite de tiempo
     * aunque el cliente no lo tenga habilitado; {@code 0} quita el límite del cliente para este método;
     * {@code -1} (por defecto) hereda el del cliente. Si el método tiene un {@link #readTimeout()} mayor que el
     * límite que le toca, el límite corta primero.
     *
     * @return el tiempo máximo en milisegundos, {@code 0} para quitarlo o {@code -1} para heredarlo
     */
    long timeLimiterTimeout() default -1;
}
