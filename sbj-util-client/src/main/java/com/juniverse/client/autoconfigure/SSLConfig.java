package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuración de SSL/TLS.
 *
 * Define el keystore y el truststore para conexiones HTTPS seguras.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SSLConfig {

    /**
     * Indica si se valida el certificado SSL.
     *
     * ADVERTENCIA: con false se desactiva la validación de certificados (solo para desarrollo).
     * Únicamente se permite con el perfil dev, local o development.
     *
     * @return true si la validación SSL está habilitada
     */
    boolean enabled() default true;

    /**
     * Ruta del keystore. Admite el prefijo {@code classpath:}.
     *
     * Ejemplo: "classpath:keystore.jks" o "/ruta/a/keystore.jks"
     *
     * @return la ruta del keystore
     */
    String keystorePath() default "";

    /**
     * Contraseña del keystore.
     *
     * Admite placeholders, por ejemplo {@code "${KEYSTORE_PASSWORD}"}; nunca subas contraseñas al
     * control de versiones.
     *
     * @return la contraseña del keystore
     */
    String keystorePassword() default "";

    /**
     * Ruta del truststore. Admite el prefijo {@code classpath:}.
     *
     * Ejemplo: "classpath:truststore.jks" o "/ruta/a/truststore.jks"
     *
     * @return la ruta del truststore
     */
    String truststorePath() default "";

    /**
     * Contraseña del truststore.
     *
     * Admite placeholders, por ejemplo {@code "${TRUSTSTORE_PASSWORD}"}; nunca subas contraseñas al
     * control de versiones.
     *
     * @return la contraseña del truststore
     */
    String truststorePassword() default "";
}
