package io.github.programming0026.juniverse.client.autoconfigure;

import io.github.programming0026.juniverse.client.config.ClientConfig;
import io.github.programming0026.juniverse.client.handler.RestHttpClientRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Activa los clientes REST declarativos.
 *
 * Se coloca en una clase de configuración; busca las interfaces anotadas con {@link RestHttpClient}
 * y registra un bean (proxy) por cada una.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RestHttpClientRegistrar.class, ClientConfig.class})
public @interface EnableRestHttpClients {

    /**
     * Paquetes donde se buscan los clientes. Si está vacío, se usa el paquete de la clase anotada.
     *
     * @return los paquetes base
     */
    String[] basePackages() default {};
}
