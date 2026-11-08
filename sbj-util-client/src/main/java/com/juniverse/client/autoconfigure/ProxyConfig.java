package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configuración de un proxy HTTP/HTTPS por el que salen las peticiones del cliente.
 *
 * El proxy está desactivado mientras {@link #host()} esté vacío. Todos los textos admiten placeholders, por
 * ejemplo {@code @ProxyConfig(host = "${proxy.host:}", port = 3128)}: con la propiedad sin definir no hay
 * proxy.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProxyConfig {

    /**
     * Nombre o IP del proxy. Vacío = sin proxy.
     *
     * @return el host del proxy
     */
    String host() default "";

    /**
     * Puerto del proxy, entre 1 y 65535. Es un texto para admitir placeholders, por ejemplo
     * {@code "${proxy.port:3128}"}.
     *
     * @return el puerto del proxy
     */
    String port() default "8080";

    /**
     * Protocolo con el que se habla con el proxy ({@code http} o {@code https}); es independiente del
     * protocolo del servicio de destino.
     *
     * @return el protocolo del proxy
     */
    String scheme() default "http";

    /**
     * Usuario para un proxy que pide autenticación básica. Vacío = sin autenticación.
     *
     * @return el usuario
     */
    String username() default "";

    /**
     * Contraseña del proxy. Usa un placeholder, por ejemplo {@code "${PROXY_PASSWORD}"}; nunca la subas al
     * control de versiones.
     *
     * @return la contraseña
     */
    String password() default "";

    /**
     * Destinos a los que se llega directamente, sin pasar por el proxy. Cada patrón es un nombre exacto
     * ({@code localhost}) o lleva un {@code *} al principio ({@code *.interno.com}) o al final
     * ({@code 10.0.*}); no distingue mayúsculas.
     *
     * @return los patrones de hosts sin proxy
     */
    String[] nonProxyHosts() default {};
}
