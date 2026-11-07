package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The Interface SSLConfig.
 * Configuration for SSL/TLS settings.
 * Configures keystore and truststore for secure HTTPS connections.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SSLConfig {

    /**
     * Ssl enabled.
     * WARNING: Setting to false disables certificate validation (development only).
     * Only allowed in dev/local environments.
     *
     * @return true, if SSL validation is enabled
     */
    boolean enabled() default true;

    /**
     * Keystore path.
     * Path to keystore file. Supports classpath: prefix.
     * Example: "classpath:keystore.jks" or "/path/to/keystore.jks"
     *
     * @return the keystore path
     */
    String keystorePath() default "";

    /**
     * Keystore password.
     * IMPORTANT: Use environment-specific configuration files.
     * Never commit passwords to version control.
     *
     * @return the keystore password
     */
    String keystorePassword() default "";

    /**
     * Truststore path.
     * Path to truststore file. Supports classpath: prefix.
     * Example: "classpath:truststore.jks" or "/path/to/truststore.jks"
     *
     * @return the truststore path
     */
    String truststorePath() default "";

    /**
     * Truststore password.
     * IMPORTANT: Use environment-specific configuration files.
     * Never commit passwords to version control.
     *
     * @return the truststore password
     */
    String truststorePassword() default "";
}
