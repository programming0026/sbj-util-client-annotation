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
 * Enables the declarative REST clients.
 *
 * It is placed on a configuration class; it looks for the interfaces annotated with {@link RestHttpClient}
 * and registers one bean (proxy) for each of them.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RestHttpClientRegistrar.class, ClientConfig.class})
public @interface EnableRestHttpClients {

    /**
     * Packages where the clients are searched for. If empty, the package of the annotated class is used.
     *
     * @return the base packages
     */
    String[] basePackages() default {};
}
