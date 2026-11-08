package com.juniverse.client.autoconfigure;


import com.juniverse.client.enums.ClientType;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.exception.ErrorHandler;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Declara un cliente REST HTTP con patrones de resiliencia integrados.
 *
 * Se coloca en una interfaz cuyos métodos usan las anotaciones de Spring Web ({@code @GetMapping},
 * {@code @PathVariable}, etc.). Incluye circuit breaker, retry, rate limiting y time limiting.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RestHttpClient {

    /**
     * Identificador único del cliente. Se usa como nombre del bean y de las instancias de Resilience4j.
     * Si está vacío, se usa el nombre de la interfaz.
     *
     * @return el nombre del cliente
     */
    String name() default "";

    /**
     * URL base del servicio REST.
     *
     * @return la URL base
     */
    String url() default "";

    /**
     * Implementación HTTP a usar (REST_CLIENT o REST_TEMPLATE).
     *
     * @return el tipo de cliente
     */
    ClientType type() default ClientType.REST_CLIENT;

    /**
     * Tiempo de espera de conexión, en milisegundos. Limita tanto el establecimiento de la conexión TCP
     * como la espera para obtener una conexión libre del pool.
     *
     * @return el tiempo de espera de conexión
     */
    long connectTimeout() default 5000;

    /**
     * Tiempo de espera de lectura, en milisegundos: cuánto se espera la respuesta (y cada lectura del
     * socket) una vez conectado.
     *
     * @return el tiempo de espera de lectura
     */
    long readTimeout() default 30000;

    /**
     * Número máximo de conexiones en el pool.
     *
     * @return el máximo de conexiones
     */
    int maxConnections() default 100;

    /**
     * Número máximo de conexiones por ruta en el pool.
     *
     * @return el máximo de conexiones por ruta
     */
    int maxConnectionsPerRoute() default 20;

    /**
     * Tiempo de vida de las conexiones, en milisegundos. Por defecto -1 (sin límite).
     *
     * @return el tiempo de vida de las conexiones
     */
    long connectionTimeToLive() default -1;

    /**
     * Configuración de SSL/TLS (keystore y truststore).
     *
     * @return la configuración SSL
     */
    SSLConfig ssl() default @SSLConfig;

    /**
     * Configuración del circuit breaker.
     *
     * @return la configuración del circuit breaker
     */
    CircuitBreakerConfig circuitBreaker() default @CircuitBreakerConfig;

    /**
     * Configuración de reintentos con backoff exponencial.
     *
     * @return la configuración de reintentos
     */
    RetryConfig retry() default @RetryConfig;

    /**
     * Configuración del rate limiter, para controlar el volumen de peticiones.
     *
     * @return la configuración del rate limiter
     */
    RateLimiterConfig rateLimiter() default @RateLimiterConfig;

    /**
     * Configuración del tiempo máximo de ejecución de las peticiones.
     *
     * @return la configuración del time limiter
     */
    TimeLimiterConfig timeLimiter() default @TimeLimiterConfig;

    /**
     * Manejador de errores para respuestas HTTP con error. Si existe un bean de ese tipo se usa; si no,
     * Spring crea la instancia con inyección de dependencias (puede recibir beans por constructor).
     *
     * @return la clase del manejador de errores
     */
    Class<? extends ErrorHandler> errorHandler() default DefaultErrorHandler.class;
}
