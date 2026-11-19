package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configuration of an HTTP/HTTPS proxy through which the client's requests go out.
 *
 * The proxy is disabled while {@link #host()} is empty. All the texts accept placeholders, for
 * example {@code @ProxyConfig(host = "${proxy.host:}", port = 3128)}: with the property undefined there is no
 * proxy.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProxyConfig {

    /**
     * Name or IP of the proxy. Empty = no proxy.
     *
     * @return the proxy host
     */
    String host() default "";

    /**
     * Proxy port, between 1 and 65535. It is a text to allow placeholders, for example
     * {@code "${proxy.port:3128}"}.
     *
     * @return the proxy port
     */
    String port() default "8080";

    /**
     * Protocol used to talk to the proxy ({@code http} or {@code https}); it is independent of the protocol of
     * the target service.
     *
     * @return the proxy scheme
     */
    String scheme() default "http";

    /**
     * User for a proxy that requires basic authentication. Empty = no authentication.
     *
     * @return the user name
     */
    String username() default "";

    /**
     * Proxy password. Use a placeholder, for example {@code "${PROXY_PASSWORD}"}; never commit it to
     * version control.
     *
     * @return the password
     */
    String password() default "";

    /**
     * Destinations that are reached directly, without going through the proxy. Each pattern is an exact name
     * ({@code localhost}) or has a {@code *} at the start ({@code *.internal.com}) or at the end
     * ({@code 10.0.*}); it is case-insensitive.
     *
     * @return the host patterns that bypass the proxy
     */
    String[] nonProxyHosts() default {};
}
