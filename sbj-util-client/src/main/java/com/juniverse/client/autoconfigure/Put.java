package com.juniverse.client.autoconfigure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Interface PutMapping.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Put {
    
    /**
     * Value.
     *
     * @return the string
     */
    String value() default "";

    /**
     * Headers.
     *
     * @return the string[]
     */
    String[] headers() default {};
}
