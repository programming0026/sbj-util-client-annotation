package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * SSL/TLS configuration.
 *
 * It defines the keystore and the truststore for secure HTTPS connections.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SSLConfig {

    /**
     * Whether the SSL certificate is validated.
     *
     * WARNING: with false certificate validation is disabled (for development only).
     * It is only allowed with the dev, local or development profile.
     *
     * @return true if SSL validation is enabled
     */
    boolean enabled() default true;

    /**
     * Path of the keystore. It accepts the {@code classpath:} prefix.
     *
     * Example: "classpath:keystore.jks" or "/path/to/keystore.jks"
     *
     * @return the keystore path
     */
    String keystorePath() default "";

    /**
     * Keystore password.
     *
     * It accepts placeholders, for example {@code "${KEYSTORE_PASSWORD}"}; never commit passwords to
     * version control.
     *
     * @return the keystore password
     */
    String keystorePassword() default "";

    /**
     * Path of the truststore. It accepts the {@code classpath:} prefix.
     *
     * Example: "classpath:truststore.jks" or "/path/to/truststore.jks"
     *
     * @return the truststore path
     */
    String truststorePath() default "";

    /**
     * Truststore password.
     *
     * It accepts placeholders, for example {@code "${TRUSTSTORE_PASSWORD}"}; never commit passwords to
     * version control.
     *
     * @return the truststore password
     */
    String truststorePassword() default "";
}
