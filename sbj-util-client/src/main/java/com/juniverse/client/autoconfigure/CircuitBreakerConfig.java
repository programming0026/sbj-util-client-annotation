package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuración del patrón Circuit Breaker de Resilience4j.
 *
 * El circuit breaker evita fallas en cascada dejando de enviar peticiones a un servicio que está fallando.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreakerConfig {

    /**
     * Indica si el circuit breaker está habilitado.
     *
     * @return true si el circuit breaker está habilitado
     */
    boolean enabled() default false;

    /**
     * Porcentaje de fallas (0-100) a partir del cual se abre el circuito.
     *
     * @return el umbral de tasa de fallas
     */
    int failureRateThreshold() default 50;

    /**
     * Número de llamadas de la ventana deslizante usada para calcular la tasa de fallas.
     *
     * @return el tamaño de la ventana deslizante
     */
    int slidingWindowSize() default 100;

    /**
     * Número mínimo de llamadas antes de que el circuit breaker pueda calcular la tasa de fallas.
     *
     * @return el número mínimo de llamadas
     */
    int minimumNumberOfCalls() default 10;

    /**
     * Tiempo en milisegundos que el circuito permanece abierto antes de pasar a semiabierto.
     *
     * @return el tiempo de espera en estado abierto, en milisegundos
     */
    long waitDurationInOpenState() default 60000;

    /**
     * Número de llamadas permitidas en estado semiabierto para comprobar si el servicio se recuperó.
     *
     * @return el número de llamadas permitidas en estado semiabierto
     */
    int permittedNumberOfCallsInHalfOpenState() default 5;
}
