package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.enums.RequestFactoryOverridePolicy;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.config.JsonMapperConfigurer;
import io.github.programming0026.juniverse.client.config.RestHttpClientCustomizer;
import org.springframework.http.converter.HttpMessageConverter;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.util.AnnotationPlaceholderResolver;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.RequestLoggingInterceptor;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.exception.ErrorHandler;
import io.github.resilience4j.core.lang.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Creates the proxy of a {@link RestHttpClient} interface.
 * It builds the RestClient or RestTemplate according to {@code type()}, obtains the {@link ErrorHandler} and
 * returns a dynamic proxy backed by {@link RestHttpClientInvocationHandler}. The proxy is created only once and
 * when the context starts ({@link #isEagerInit()}), even if nobody injects it, so that a badly
 * declared client makes startup fail; when the context closes the connection pool is released.
 *
 * @param <T> the type of the client interface
 * @author Java Universe
 */
public class RestHttpClientFactoryBean<T> implements SmartFactoryBean<T>, ApplicationContextAware, DisposableBean {

    /** The client interface. */
    private final Class<T> clientInterface;

    /** The factory of Resilience4j instances. */
    private final CircuitBreakerUtil circuitBreakerUtil;

    /** The utility that builds the HTTP clients. */
    private final RestClientUtil restClientUtil;

    /** The application context. */
    private ApplicationContext applicationContext;

    /** The created proxy, or null if it has not been created yet. */
    private T proxy;

    /** The proxy's request factory (owner of the connection pool), or null if it has not been created yet. */
    private HttpComponentsClientHttpRequestFactory requestFactory;

    /**
     * Creates the factory bean.
     *
     * @param clientInterface the client interface (cannot be null)
     * @param circuitBreakerUtil the factory of Resilience4j instances
     * @param restClientUtil the utility that builds the HTTP clients
     */
    public RestHttpClientFactoryBean(Class<T> clientInterface,
                                     CircuitBreakerUtil circuitBreakerUtil,
                                     RestClientUtil restClientUtil) {
        this.clientInterface = Objects.requireNonNull(clientInterface);
        this.circuitBreakerUtil = circuitBreakerUtil;
        this.restClientUtil = restClientUtil;
    }

    /**
     * Stores the application context.
     *
     * @param applicationContext the application context
     * @throws BeansException if a Spring error occurs
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Gets the application context.
     *
     * @return the application context
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    /**
     * Returns the client's proxy, creating it the first time.
     *
     * @return the proxy that implements the client interface
     * @throws Exception if the error handler cannot be created
     */
    @SuppressWarnings("unchecked")
    @Override
    public synchronized T getObject() throws Exception {
        if (proxy != null) {
            return proxy;
        }
        UnaryOperator<String> placeholderResolver = placeholderResolver();
        RestHttpClient annotation = AnnotationPlaceholderResolver.resolve(
                clientInterface.getAnnotation(RestHttpClient.class), placeholderResolver);

        ErrorHandler errorHandler = resolveComponent(annotation.errorHandler());
        validateFallback(annotation.fallback());
        Object fallback = annotation.fallback() == void.class ? null : resolveComponent(annotation.fallback());
        JsonMapperConfigurer jsonConfigurer = annotation.jsonConfig() == JsonMapperConfigurer.class ? null
                : resolveComponent(annotation.jsonConfig());
        RequestFactoryGuard guard = new RequestFactoryGuard(
                clientName(annotation),
                RequestFactoryOverridePolicy.from(applicationContext == null ? null
                        : applicationContext.getEnvironment().getProperty(RequestFactoryOverridePolicy.PROPERTY)));
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        if (annotation.logging()) {
            interceptors.add(new RequestLoggingInterceptor(clientName(annotation)));
        }
        for (Class<? extends ClientHttpRequestInterceptor> type : annotation.interceptors()) {
            interceptors.add(resolveComponent(type));
        }
        HttpComponentsClientHttpRequestFactory factory = restClientUtil.createRequestFactory(annotation);
        RestHttpClientInvocationHandler handler;
        try {
            Object httpClient = switch (annotation.type()) {
                case REST_CLIENT -> buildRestClient(annotation, factory, interceptors, guard, jsonConfigurer);
                case REST_TEMPLATE -> buildRestTemplate(annotation, factory, interceptors, guard, jsonConfigurer);
            };
            handler = new RestHttpClientInvocationHandler(
                    httpClient,
                    annotation,
                    errorHandler,
                    clientInterface,
                    circuitBreakerUtil,
                    placeholderResolver,
                    fallback
            );
        } catch (RuntimeException e) {
            // The client is invalid (for example, a badly declared method): the already created pool is released.
            factory.destroy();
            throw e;
        }

        this.requestFactory = factory;
        this.proxy = (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                handler
        );
        return proxy;
    }

    /**
     * Checks that the fallback class is suitable to replace the client.
     *
     * @param fallbackType the class declared in {@code fallback()} ({@code void.class} if there is none)
     * @throws ClientException if it does not implement the client interface or is not a concrete class
     */
    private void validateFallback(Class<?> fallbackType) {
        if (fallbackType == void.class) {
            return;
        }
        if (!clientInterface.isAssignableFrom(fallbackType) || fallbackType.isInterface()
                || Modifier.isAbstract(fallbackType.getModifiers())) {
            throw new ClientException("Fallback " + fallbackType.getName() + " of client "
                    + clientInterface.getSimpleName() + " must be a concrete class that implements "
                    + clientInterface.getName());
        }
    }

    /**
     * Releases the client's connection pool. Spring calls it when the context closes.
     *
     * @throws Exception if closing the HTTP client fails
     */
    @Override
    public synchronized void destroy() throws Exception {
        if (requestFactory != null) {
            requestFactory.destroy();
            requestFactory = null;
        }
    }

    /**
     * Obtains a component declared by class in the annotation (error handler, interceptors). With a
     * Spring context, it uses the bean of that type if there is only one and, otherwise, creates an instance
     * with dependency injection (it can use {@code @Autowired} or a constructor with beans). Without a context,
     * it instantiates it with its no-argument constructor.
     *
     * @param type the component class
     * @param <C> the component type
     * @return the component
     * @throws NoSuchMethodException if it cannot be instantiated
     */
    private <C> C resolveComponent(Class<C> type) throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        AutowireCapableBeanFactory beanFactory = beanFactory();
        if (beanFactory == null) {
            return type.getDeclaredConstructor().newInstance();
        }
        if (beanFactory instanceof ListableBeanFactory listableBeanFactory) {
            C bean = listableBeanFactory.getBeanProvider(type).getIfUnique();
            if (bean != null) {
                return bean;
            }
        }
        return beanFactory.createBean(type);
    }

    /**
     * Creates the RestClient. It starts from the client's request factory and URL and then applies the
     * application's {@link RestClientCustomizer}s in their order. This way the same configuration as Spring
     * Boot's {@code RestClient.Builder} is used (message converters with the application's Jackson,
     * observability, interceptors), without requesting that bean: Boot creates it with its own request factory
     * and pool, which would be left unclosed.
     * Since the customizers go afterwards, a test can replace the request factory with a
     * {@code MockRestServiceServer}; the {@code guard} prevents a customizer meant for other clients from
     * doing so by accident (see {@link RequestFactoryOverridePolicy}).
     *
     * @param annotation the client configuration
     * @param factory the client's request factory
     * @param interceptors the client's own interceptors, which are added after the global ones
     * @param guard watches over replacements of the request factory
     * @param jsonConfigurer the client's JSON configurer, or {@code null} if it declares none
     * @return the RestClient
     */
    private RestClient buildRestClient(RestHttpClient annotation, HttpComponentsClientHttpRequestFactory factory,
                                       List<ClientHttpRequestInterceptor> interceptors, RequestFactoryGuard guard,
                                       JsonMapperConfigurer jsonConfigurer) {
        RestClient.Builder builder = restClientUtil.restClientBuilder(annotation, factory);
        AutowireCapableBeanFactory beanFactory = beanFactory();
        if (beanFactory instanceof ListableBeanFactory listableBeanFactory) {
            listableBeanFactory.getBeanProvider(RestClientCustomizer.class).orderedStream()
                    .forEach(customizer -> guard.customize(builder, customizer));
            if (jsonConfigurer != null) {
                HttpMessageConverter<?> json = ClientJson.converter(listableBeanFactory, jsonConfigurer);
                builder.configureMessageConverters(converters -> converters.registerDefaults().withJsonConverter(json));
            }
            String clientName = clientName(annotation);
            listableBeanFactory.getBeanProvider(RestHttpClientCustomizer.class).orderedStream()
                    .forEach(customizer -> guard.customizeBuilder(builder,
                            guarded -> customizer.customize(clientName, guarded),
                            "RestHttpClientCustomizer " + customizer.getClass().getName()));
        }
        interceptors.forEach(builder::requestInterceptor);
        return builder.build();
    }

    /**
     * Creates the RestTemplate on top of the client's request factory. If the application has a
     * {@link RestTemplateBuilder} (Spring Boot), it uses it to inherit its message converters, interceptors
     * and customizers; its global {@code clientSettings} are discarded so as not to override the client's own
     * timeouts and SSL.
     * The builder's customizers are applied after the client's request factory (so a test can
     * replace it with a {@code MockRestServiceServer}); the {@code guard} checks the result and restores the
     * client's one if the replacement is not acceptable. Then the {@link RestHttpClientCustomizer}s are applied.
     *
     * @param annotation the client configuration
     * @param factory the client's request factory
     * @param interceptors the client's own interceptors, which are added after the global ones
     * @param guard watches over replacements of the request factory
     * @param jsonConfigurer the client's JSON configurer, or {@code null} if it declares none
     * @return the RestTemplate
     */
    private RestTemplate buildRestTemplate(RestHttpClient annotation, HttpComponentsClientHttpRequestFactory factory,
                                           List<ClientHttpRequestInterceptor> interceptors,
                                           RequestFactoryGuard guard,
                                           JsonMapperConfigurer jsonConfigurer) {
        AutowireCapableBeanFactory beanFactory = beanFactory();
        RestTemplateBuilder builder = beanFactory instanceof ListableBeanFactory listableBeanFactory
                ? listableBeanFactory.getBeanProvider(RestTemplateBuilder.class).getIfAvailable()
                : null;
        RestTemplate template = builder == null
                ? restClientUtil.configureRestTemplate(factory)
                : builder.clientSettings(HttpClientSettings.defaults()).requestFactory(() -> factory).build();
        guard.guardTemplate(template, factory);
        if (jsonConfigurer != null) {
            List<HttpMessageConverter<?>> converters = new ArrayList<>(template.getMessageConverters());
            ClientJson.install(converters, ClientJson.converter(
                    beanFactory instanceof ListableBeanFactory listable ? listable : null, jsonConfigurer));
            template.setMessageConverters(converters);
        }
        if (beanFactory instanceof ListableBeanFactory listableBeanFactory) {
            String clientName = clientName(annotation);
            listableBeanFactory.getBeanProvider(RestHttpClientCustomizer.class).orderedStream()
                    .forEach(customizer -> guard.customizeTemplate(template,
                            target -> customizer.customize(clientName, target),
                            "RestHttpClientCustomizer " + customizer.getClass().getName()));
        }
        template.getInterceptors().addAll(interceptors);
        return template;
    }

    /**
     * Gets the client name seen by the {@link RestHttpClientCustomizer}s: the {@code name()} of the
     * annotation or, if empty, the simple name of the interface.
     *
     * @param annotation the client configuration (with placeholders resolved)
     * @return the client name
     */
    private String clientName(RestHttpClient annotation) {
        return annotation.name().isEmpty() ? clientInterface.getSimpleName() : annotation.name();
    }

    /**
     * Gets the context's bean factory. The one from {@link ConfigurableApplicationContext} is used because it is
     * available even before {@code refresh()}.
     *
     * @return the bean factory, or null if there is no context
     */
    private AutowireCapableBeanFactory beanFactory() {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext instanceof ConfigurableApplicationContext configurable
                ? configurable.getBeanFactory()
                : applicationContext.getAutowireCapableBeanFactory();
    }

    /**
     * Creates the function that resolves {@code ${...}} placeholders (with an optional default value
     * {@code ${key:value}}) using the context's {@link Environment}. Without a context, for example when
     * creating the factory bean by hand, it uses a {@link StandardEnvironment} (system properties and
     * environment variables).
     *
     * @return the placeholder resolver
     */
    private UnaryOperator<String> placeholderResolver() {
        Environment environment = applicationContext != null
                ? applicationContext.getEnvironment()
                : new StandardEnvironment();
        return value -> {
            try {
                return environment.resolveRequiredPlaceholders(value);
            } catch (IllegalArgumentException e) {
                throw new ClientException("Cannot resolve placeholder in '" + value + "' for client "
                        + clientInterface.getSimpleName(), e);
            }
        };
    }

    /**
     * Gets the type of the created object.
     *
     * @return the client interface
     */
    @Override
    public Class<?> getObjectType() {
        return clientInterface;
    }

    /**
     * Indicates that the proxy is a singleton.
     *
     * @return always true
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * Requests creating the proxy when the context starts, to validate the client (URL and methods) right away.
     *
     * @return always true
     */
    @Override
    public boolean isEagerInit() {
        return true;
    }


}
