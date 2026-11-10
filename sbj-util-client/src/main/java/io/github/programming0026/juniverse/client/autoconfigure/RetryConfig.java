package io.github.programming0026.juniverse.client.autoconfigure;

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
 *
 * @author Java Universe
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

    /**
     * Indica si las peticiones no idempotentes (POST y PATCH) se reintentan igual que las idempotentes.
     *
     * Por defecto (false) solo se reintentan cuando falla la conexión, es decir, cuando es seguro que la
     * petición no llegó al servidor. Con true también se reintentan tras errores HTTP, timeouts o cortes de
     * conexión, lo que puede duplicar la operación: úsalo solo si el servicio las trata de forma idempotente
     * (por ejemplo, con una clave de idempotencia).
     *
     * @return true para reintentar POST y PATCH sin restricciones
     */
    boolean retryNonIdempotent() default false;
}
