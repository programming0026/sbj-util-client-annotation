package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.util.AnnotationPlaceholderResolver;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.exception.ErrorHandler;
import io.github.resilience4j.core.lang.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Crea el proxy de una interfaz {@link RestHttpClient}.
 *
 * Construye el RestClient o RestTemplate según {@code type()}, obtiene el {@link ErrorHandler} y devuelve
 * un proxy dinámico respaldado por {@link RestHttpClientInvocationHandler}. El proxy se crea una sola vez;
 * al cerrarse el contexto se libera el pool de conexiones.
 *
 * @param <T> el tipo de la interfaz del cliente
 */
public class RestHttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware, DisposableBean {

    /** La interfaz del cliente. */
    private final Class<T> clientInterface;

    /** La fábrica de instancias de Resilience4j. */
    private final CircuitBreakerUtil circuitBreakerUtil;

    /** La utilidad que construye los clientes HTTP. */
    private final RestClientUtil restClientUtil;

    /** El contexto de la aplicación. */
    private ApplicationContext applicationContext;

    /** El proxy creado, o null si todavía no se creó. */
    private T proxy;

    /** La request factory del proxy (dueña del pool de conexiones), o null si todavía no se creó. */
    private HttpComponentsClientHttpRequestFactory requestFactory;

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
     * Devuelve el proxy del cliente, creándolo la primera vez.
     *
     * @return el proxy que implementa la interfaz del cliente
     * @throws Exception si no se puede crear el manejador de errores
     */
    @SuppressWarnings("unchecked")
    @Override
    public synchronized T getObject() throws Exception {
        if (proxy != null) {
            return proxy;
        }
        UnaryOperator<String> placeholderResolver = placeholderResolver();
        // Los placeholders ${...} de la anotación (url, SSL, etc.) se resuelven una sola vez aquí.
        RestHttpClient annotation = AnnotationPlaceholderResolver.resolve(
                clientInterface.getAnnotation(RestHttpClient.class), placeholderResolver);

        ErrorHandler errorHandler = createErrorHandler(annotation.errorHandler());
        HttpComponentsClientHttpRequestFactory factory = restClientUtil.createRequestFactory(annotation);
        Object httpClient = switch (annotation.type()) {
            case REST_CLIENT -> restClientUtil.configureRestClient(annotation, factory);
            case REST_TEMPLATE -> restClientUtil.configureRestTemplate(factory);
        };

        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(
                httpClient,
                annotation,
                errorHandler,
                clientInterface,
                circuitBreakerUtil,
                placeholderResolver
        );

        this.requestFactory = factory;
        this.proxy = (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                handler
        );
        return proxy;
    }

    /**
     * Libera el pool de conexiones del cliente. Spring lo llama al cerrar el contexto.
     *
     * @throws Exception si falla el cierre del cliente HTTP
     */
    @Override
    public synchronized void destroy() throws Exception {
        if (requestFactory != null) {
            requestFactory.destroy();
            requestFactory = null;
        }
    }

    /**
     * Obtiene el manejador de errores. Con contexto de Spring, usa el bean de ese tipo si hay uno solo y,
     * si no, crea una instancia con inyección de dependencias (puede usar {@code @Autowired} o constructor
     * con beans). Sin contexto, lo instancia con su constructor sin argumentos.
     *
     * @param type la clase del manejador de errores
     * @return el manejador de errores
     * @throws Exception si no se puede instanciar
     */
    private ErrorHandler createErrorHandler(Class<? extends ErrorHandler> type) throws Exception {
        if (applicationContext == null) {
            return type.getDeclaredConstructor().newInstance();
        }
        AutowireCapableBeanFactory beanFactory =
                applicationContext instanceof ConfigurableApplicationContext configurable
                        ? configurable.getBeanFactory()
                        : applicationContext.getAutowireCapableBeanFactory();
        if (beanFactory instanceof ListableBeanFactory listableBeanFactory) {
            ErrorHandler bean = listableBeanFactory.getBeanProvider(type).getIfUnique();
            if (bean != null) {
                return bean;
            }
        }
        return beanFactory.createBean(type);
    }

    /**
     * Crea la función que resuelve placeholders {@code ${...}} (con valor por defecto opcional
     * {@code ${clave:valor}}) usando el {@link Environment} del contexto. Sin contexto, por ejemplo al
     * crear el factory bean a mano, usa un {@link StandardEnvironment} (propiedades del sistema y
     * variables de entorno).
     *
     * @return el resolvedor de placeholders
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
