package com.juniverse.client.autoconfigure;

import com.juniverse.client.config.ClientConfig;
import com.juniverse.client.handler.RestHttpClientRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The Interface EnableRestHttpClients.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RestHttpClientRegistrar.class, ClientConfig.class})
public @interface EnableRestHttpClients {
    
    /**
     * Base packages.
     *
     * @return the string[]
     */
    String[] basePackages() default {};
}
