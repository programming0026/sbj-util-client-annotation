package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuración del patrón Rate Limiter de Resilience4j.
 *
 * Limita el número de peticiones permitidas en un periodo de tiempo.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiterConfig {

    /**
     * Indica si el rate limiter está habilitado.
     *
     * @return true si el rate limiter está habilitado
     */
    boolean enabled() default false;

    /**
     * Número máximo de peticiones permitidas por periodo de refresco.
     *
     * @return el límite por periodo
     */
    int limitForPeriod() default 100;

    /**
     * Duración en milisegundos del periodo en el que aplica el límite.
     *
     * @return el periodo de refresco en milisegundos
     */
    long limitRefreshPeriod() default 1000;

    /**
     * Tiempo máximo en milisegundos que un hilo espera a obtener permiso.
     *
     * @return el tiempo de espera en milisegundos
     */
    long timeoutDuration() default 5000;
}
