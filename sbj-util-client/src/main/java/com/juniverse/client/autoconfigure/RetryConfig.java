package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuración del patrón Retry de Resilience4j.
 *
 * Reintenta automáticamente las peticiones fallidas con espera configurable y backoff exponencial.
 * Siempre está activo; qué errores se reintentan lo decide {@code ErrorHandler.shouldRetry}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryConfig {

    /**
     * Número máximo de intentos, incluido el primero (1 = sin reintentos).
     *
     * @return el número máximo de intentos
     */
    int maxRetries() default 3;

    /**
     * Espera inicial entre reintentos, en milisegundos.
     *
     * @return la espera inicial en milisegundos
     */
    long retryDelay() default 1000;

    /**
     * Multiplicador del backoff exponencial.
     *
     * Cada reintento espera {@code retryDelay * multiplicador^(intento - 1)}.
     * Por ejemplo, con retryDelay=1000 y multiplicador=2.0: 1 s, 2 s, 4 s, 8 s...
     *
     * @return el multiplicador del backoff exponencial
     */
    double exponentialBackoffMultiplier() default 2.0;
}
