package com.juniverse.client.autoconfigure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Interface RequestParam.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParam {
    
    /**
     * Value.
     *
     * @return the string
     */
    String value() default "";

    /**
     * Required.
     *
     * @return true, if successful
     */
    boolean required() default true;
}
