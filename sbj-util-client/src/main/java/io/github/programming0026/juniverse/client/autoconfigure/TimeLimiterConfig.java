package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuración del patrón Time Limiter de Resilience4j.
 *
 * Fija un tiempo máximo de ejecución para las peticiones. Si se excede, la llamada lanza
 * {@code ClientException} con un {@code TimeoutException} como causa.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TimeLimiterConfig {

    /**
     * Indica si el time limiter está habilitado.
     *
     * @return true si el time limiter está habilitado
     */
    boolean enabled() default false;

    /**
     * Tiempo máximo de ejecución en milisegundos.
     *
     * @return el tiempo máximo en milisegundos
     */
    long timeout() default 30000;
}
