package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.enums.RequestFactoryOverridePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Ensures that customizers do not accidentally remove a client's request factory (the one that carries its
 * connection pool, timeouts and SSL), according to the {@link RequestFactoryOverridePolicy}.
 * Whenever a customizer replaces the request factory, the library knows which one it was and logs it.
 *
 * @author Java Universe
 */
final class RequestFactoryGuard {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RequestFactoryGuard.class);

    /** The client name, for the log messages. */
    private final String clientName;

    /** The policy that decides which replacements are accepted. */
    private final RequestFactoryOverridePolicy policy;

    /**
     * Creates the guard of a client.
     *
     * @param clientName the client name
     * @param policy the replacement policy
     */
    RequestFactoryGuard(String clientName, RequestFactoryOverridePolicy policy) {
        this.clientName = clientName;
        this.policy = policy;
    }

    /**
     * Applies a {@link RestClientCustomizer} to the builder, intercepting its calls to
     * {@code requestFactory(...)}: a replacement that the policy does not accept is ignored.
     *
     * @param builder the builder, which already carries the library's request factory
     * @param customizer the customizer to apply
     */
    void customize(RestClient.Builder builder, RestClientCustomizer customizer) {
        customizer.customize(guarded(builder, "RestClientCustomizer " + customizer.getClass().getName()));
    }

    /**
     * Checks an already built {@link RestTemplate} (with the customizers applied): if its request factory is no
     * longer the library's one and the policy does not accept the replacement, it restores the library's one.
     *
     * @param template the RestTemplate
     * @param libraryFactory the library's request factory
     */
    void guardTemplate(RestTemplate template, ClientHttpRequestFactory libraryFactory) {
        guardTemplate(template, libraryFactory, "A RestTemplateBuilder customizer");
    }

    /**
     * Same as {@link #guardTemplate(RestTemplate, ClientHttpRequestFactory)} but indicating who may have
     * made the replacement, for the log.
     *
     * @param template the RestTemplate
     * @param previousFactory the request factory it had before the customization, to which it reverts
     *                        if the replacement is not accepted
     * @param source who customized the RestTemplate
     */
    void guardTemplate(RestTemplate template, ClientHttpRequestFactory previousFactory, String source) {
        ClientHttpRequestFactory current = currentRequestFactory(template);
        if (current != previousFactory && !accept(source, current)) {
            template.setRequestFactory(previousFactory);
        }
    }

    /**
     * Applies the customization of a {@code RestHttpClientCustomizer} to the RestTemplate and checks that it
     * has not replaced the request factory without the policy accepting it.
     *
     * @param template the RestTemplate
     * @param customizer the customization
     * @param source who customizes, for the log
     */
    void customizeTemplate(RestTemplate template, Consumer<RestTemplate> customizer, String source) {
        ClientHttpRequestFactory before = currentRequestFactory(template);
        customizer.accept(template);
        guardTemplate(template, before, source);
    }

    /**
     * Applies the customization of a {@code RestHttpClientCustomizer} to the builder of a RestClient,
     * intercepting its calls to {@code requestFactory(...)}.
     *
     * @param builder the builder
     * @param customizer the customization
     * @param source who customizes, for the log
     */
    void customizeBuilder(RestClient.Builder builder, Consumer<RestClient.Builder> customizer, String source) {
        customizer.accept(guarded(builder, source));
    }

    /**
     * Gets the RestTemplate's request factory without the interceptors' wrapper: with interceptors,
     * {@code getRequestFactory()} returns a factory that applies them, not the configured one.
     *
     * @param template the RestTemplate
     * @return the configured request factory
     */
    private static ClientHttpRequestFactory currentRequestFactory(RestTemplate template) {
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(template.getInterceptors());
        template.getInterceptors().clear();
        try {
            return template.getRequestFactory();
        } finally {
            template.getInterceptors().addAll(interceptors);
        }
    }

    /**
     * Decides whether the replacement is accepted and leaves a record in the log.
     *
     * @param source who did it
     * @param replacement the request factory that replaces the library's one, or null if Spring's
     *                    default one is requested
     * @return true if it is accepted
     */
    private boolean accept(String source, ClientHttpRequestFactory replacement) {
        String replacementName = replacement == null ? "null (the default one)" : replacement.getClass().getName();
        if (policy.accepts(replacement)) {
            if (RequestFactoryOverridePolicy.isTestDouble(replacement)) {
                LOG.debug("{} replaced the request factory of client {} with the test double {}", source,
                        clientName, replacementName);
            } else {
                LOG.warn("{} replaced the request factory of client {} with {}: the connection pool, timeouts and "
                        + "SSL of @RestHttpClient no longer apply to it", source, clientName, replacementName);
            }
            return true;
        }
        if (LOG.isWarnEnabled()) {
            LOG.warn("Ignored the request factory {} set by {} for client {}: it would drop the connection pool, "
                            + "timeouts and SSL of @RestHttpClient. Set {}=allow to accept it (currently '{}')",
                    replacementName, source, clientName, RequestFactoryOverridePolicy.PROPERTY,
                    policy.name().toLowerCase(Locale.ROOT));
        }
        return false;
    }

    /**
     * Creates a builder that delegates to the real one, but consults the policy when
     * {@code requestFactory(...)} is called. Chained calls and {@code apply(...)} keep going through the
     * guarded builder.
     *
     * @param delegate the real builder
     * @param source who customizes (customizer type and class), for the log
     * @return the guarded builder
     */
    @SuppressWarnings("unchecked")
    private RestClient.Builder guarded(RestClient.Builder delegate, String source) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }
            if ("requestFactory".equals(method.getName()) && args != null && args.length == 1
                    && !accept(source, (ClientHttpRequestFactory) args[0])) {
                return proxy;
            }
            if ("apply".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof Consumer<?> consumer) {
                ((Consumer<RestClient.Builder>) consumer).accept((RestClient.Builder) proxy);
                return proxy;
            }
            try {
                Object result = method.invoke(delegate, args);
                return result == delegate ? proxy : result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (RestClient.Builder) Proxy.newProxyInstance(RestClient.Builder.class.getClassLoader(),
                new Class<?>[]{RestClient.Builder.class}, handler);
    }
}
