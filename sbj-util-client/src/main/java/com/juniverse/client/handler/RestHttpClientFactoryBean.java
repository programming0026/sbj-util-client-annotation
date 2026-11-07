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
 * Crea el proxy de una interfaz {@link RestHttpClient}.
 *
 * Construye el RestClient o RestTemplate según {@code type()}, instancia el {@link ErrorHandler} por
 * reflexión y devuelve un proxy dinámico respaldado por {@link RestHttpClientInvocationHandler}.
 *
 * @param <T> el tipo de la interfaz del cliente
 */
public class RestHttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    /** La interfaz del cliente. */
    private final Class<T> clientInterface;

    /** La fábrica de instancias de Resilience4j. */
    private final CircuitBreakerUtil circuitBreakerUtil;

    /** La utilidad que construye los clientes HTTP. */
    private final RestClientUtil restClientUtil;

    /** El contexto de la aplicación. */
    private ApplicationContext applicationContext;
    /**
     * Crea el factory bean.
     *
     * @param clientInterface la interfaz del cliente (no puede ser null)
     * @param circuitBreakerUtil la fábrica de instancias de Resilience4j
     * @param restClientUtil la utilidad que construye los clientes HTTP
     */
    public RestHttpClientFactoryBean(Class<T> clientInterface,
                                     CircuitBreakerUtil circuitBreakerUtil,
                                     RestClientUtil restClientUtil) {
        this.clientInterface = Objects.requireNonNull(clientInterface);
        this.circuitBreakerUtil = circuitBreakerUtil;
        this.restClientUtil = restClientUtil;
    }

    /**
     * Guarda el contexto de la aplicación.
     *
     * @param applicationContext el contexto de la aplicación
     * @throws BeansException si ocurre un error de Spring
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Obtiene el contexto de la aplicación.
     *
     * @return el contexto de la aplicación
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    /**
     * Crea el proxy del cliente.
     *
     * @return el proxy que implementa la interfaz del cliente
     * @throws Exception si no se puede instanciar el manejador de errores
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
     * Crea el cliente HTTP según el {@code type()} de la anotación.
     *
     * @param annotation la anotación del cliente
     * @return un RestClient o un RestTemplate
     */
    private Object createHttpClient(RestHttpClient annotation) {
        return switch (annotation.type()) {
            case REST_CLIENT -> restClientUtil.configureRestClient(annotation);
            case REST_TEMPLATE -> restClientUtil.configureRestTemplate(annotation);

        };
    }

    /**
     * Obtiene el tipo del objeto creado.
     *
     * @return la interfaz del cliente
     */
    @Override
    public Class<?> getObjectType() {
        return clientInterface;
    }

    /**
     * Indica que el proxy es singleton.
     *
     * @return siempre true
     */
    @Override
    public boolean isSingleton() {
        return true;
    }


}
