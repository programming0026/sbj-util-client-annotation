package io.github.programming0026.juniverse.client.autoconfigure;


import io.github.programming0026.juniverse.client.config.JsonMapperConfigurer;
import io.github.programming0026.juniverse.client.enums.ClientType;
import io.github.programming0026.juniverse.client.exception.DefaultErrorHandler;
import io.github.programming0026.juniverse.client.exception.ErrorHandler;
import org.springframework.http.client.ClientHttpRequestInterceptor;
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
 *
 * @author Java Universe
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
     * Si el cliente sigue las redirecciones del servidor (301, 302, 303, 307 y 308). Con {@code false} la
     * respuesta 3xx se entrega tal cual: se puede leer con un retorno {@code ResponseEntity<T>} y su header
     * {@code Location}.
     *
     * @return true para seguir las redirecciones
     */
    boolean followRedirects() default true;

    /**
     * Máximo de redirecciones seguidas en una misma petición; al superarlo la petición falla. Solo aplica si
     * {@link #followRedirects()} es {@code true}.
     *
     * @return el máximo de redirecciones, al menos 1
     */
    int maxRedirects() default 50;

    /**
     * Proxy HTTP/HTTPS por el que salen las peticiones. Desactivado mientras su {@code host} esté vacío.
     *
     * @return la configuración del proxy
     */
    ProxyConfig proxy() default @ProxyConfig;

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

    /**
     * Interceptores de este cliente (por ejemplo, autenticación o logging), en el orden en que se aplican.
     * Igual que el manejador de errores: si existe un bean de ese tipo se usa; si no, Spring crea la
     * instancia con inyección de dependencias. Se agregan después de los interceptores globales de la
     * aplicación ({@code RestClientCustomizer}/{@code RestTemplateBuilder}).
     *
     * @return las clases de los interceptores
     */
    Class<? extends ClientHttpRequestInterceptor>[] interceptors() default {};

    /**
     * Registra cada petición HTTP (método, URL, estado y duración) en el logger
     * {@code <paquete de la librería>.http.<nombre del cliente>} (ver {@code Constants.BASE}), a nivel INFO,
     * o WARN si falla. Nunca registra headers ni cuerpos, y de la query solo los nombres de los parámetros. Se
     * aplica antes que los {@link #interceptors()}, así que la duración incluye lo que ellos hagan.
     *
     * @return true para activar el registro de peticiones
     */
    boolean logging() default false;

    /**
     * Configurador del JSON (Jackson 3) de este cliente, que parte del {@code JsonMapper} de la aplicación y
     * cambia solo lo que declare (naming strategy, fechas, propiedades desconocidas...). La interfaz
     * {@link JsonMapperConfigurer} indica que no hay configuración propia. Igual que el manejador de errores:
     * si existe un bean de ese tipo se usa; si no, Spring crea la instancia con inyección de dependencias.
     *
     * @return la clase del configurador
     */
    Class<? extends JsonMapperConfigurer> jsonConfig() default JsonMapperConfigurer.class;

    /**
     * Clase que responde cuando el servicio no está disponible. Debe implementar la interfaz del cliente y
     * ser concreta; cada método de la clase atiende al método de la interfaz con la misma firma. Se usa cuando,
     * agotados los reintentos, la llamada falla por indisponibilidad: respuesta {@code 5xx}, error de red,
     * timeout, circuito abierto o límite de peticiones. Los errores {@code 4xx} y los errores de uso (por
     * ejemplo, un parámetro requerido ausente) <b>no</b> activan el fallback: se propagan al llamador.
     *
     * La clase se obtiene como el manejador de errores (bean único de ese tipo o instancia creada con
     * inyección de dependencias). No la declares como bean que implemente la interfaz del cliente, o la
     * inyección de esa interfaz dejaría de ser unívoca. {@code void.class} (el valor por defecto) significa
     * que no hay fallback.
     *
     * @return la clase del fallback, o {@code void.class} si no hay
     */
    Class<?> fallback() default void.class;
}
