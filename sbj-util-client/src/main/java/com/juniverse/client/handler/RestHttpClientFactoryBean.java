package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.exception.ErrorHandler;
import io.github.resilience4j.core.lang.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;
import java.util.Objects;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * The Class RestHttpClientFactoryBean.
 *
 * @param <T> the generic type
 */
public class RestHttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {
    
    /** The client interface. */
    private final Class<T> clientInterface;
    
    /** The resilience 4 j configuration. */
    private final CircuitBreakerUtil circuitBreakerUtil;

    /** The rest client. */
    private final RestClientUtil restClientUtil;

    /** The application context. */
    private ApplicationContext applicationContext;
    /**
     * Instantiates a new rest http client factory bean.
     *
     * @param clientInterface the client interface
     * @param circuitBreakerUtil the resilience 4 j configuration
     * @param restClientUtil The rest client
     */
    public RestHttpClientFactoryBean(Class<T> clientInterface,
                                     CircuitBreakerUtil circuitBreakerUtil,
                                     RestClientUtil restClientUtil) {
        this.clientInterface = Objects.requireNonNull(clientInterface);
        this.circuitBreakerUtil = circuitBreakerUtil;
        this.restClientUtil = restClientUtil;
    }

    /**
     * Set Application Context.
     *
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException the exception
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    
    /**
     * Gets the object.
     *
     * @return the object
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @Override
    public T getObject() throws Exception {
        RestHttpClient annotation = clientInterface.getAnnotation(RestHttpClient.class);

        Object httpClient = createHttpClient(annotation);
        ErrorHandler errorHandler = annotation.errorHandler().getDeclaredConstructor().newInstance();

        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(
                httpClient,
                annotation,
                errorHandler,
                clientInterface,
                circuitBreakerUtil
        );

        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                handler
        );
    }

    /**
     * Creates the http client.
     *
     * @param annotation the annotation
     * @return the object
     */
    private Object createHttpClient(RestHttpClient annotation) {
        return switch (annotation.type()) {
            case REST_CLIENT -> restClientUtil.configureRestClient(annotation);
            case REST_TEMPLATE -> restClientUtil.configureRestTemplate(annotation);

        };
    }

    /**
     * Gets the object type.
     *
     * @return the object type
     */
    @Override
    public Class<?> getObjectType() {
        return clientInterface;
    }

    /**
     * Checks if is singleton.
     *
     * @return true, if is singleton
     */
    @Override
    public boolean isSingleton() {
        return true;
    }


}
