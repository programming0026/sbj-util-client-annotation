package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.config.RestHttpClientCustomizer;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.util.AnnotationPlaceholderResolver;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.exception.ErrorHandler;
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
 * Crea el proxy de una interfaz {@link RestHttpClient}.
 *
 * Construye el RestClient o RestTemplate según {@code type()}, obtiene el {@link ErrorHandler} y devuelve
 * un proxy dinámico respaldado por {@link RestHttpClientInvocationHandler}. El proxy se crea una sola vez y
 * al arrancar el contexto ({@link #isEagerInit()}), aunque nadie lo inyecte, para que un cliente mal
 * declarado haga fallar el arranque; al cerrarse el contexto se libera el pool de conexiones.
 *
 * @param <T> el tipo de la interfaz del cliente
 */
public class RestHttpClientFactoryBean<T> implements SmartFactoryBean<T>, ApplicationContextAware, DisposableBean {

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

        ErrorHandler errorHandler = resolveComponent(annotation.errorHandler());
        validateFallback(annotation.fallback());
        Object fallback = annotation.fallback() == void.class ? null : resolveComponent(annotation.fallback());
        RequestFactoryGuard guard = new RequestFactoryGuard(
                clientName(annotation),
                RequestFactoryOverridePolicy.from(applicationContext == null ? null
                        : applicationContext.getEnvironment().getProperty(RequestFactoryOverridePolicy.PROPERTY)));
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        for (Class<? extends ClientHttpRequestInterceptor> type : annotation.interceptors()) {
            interceptors.add(resolveComponent(type));
        }
        HttpComponentsClientHttpRequestFactory factory = restClientUtil.createRequestFactory(annotation);
        RestHttpClientInvocationHandler handler;
        try {
            Object httpClient = switch (annotation.type()) {
                case REST_CLIENT -> buildRestClient(annotation, factory, interceptors, guard);
                case REST_TEMPLATE -> buildRestTemplate(annotation, factory, interceptors, guard);
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
            // El cliente es inválido (por ejemplo, un método mal declarado): se libera el pool ya creado.
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
     * Comprueba que la clase del fallback sirva para reemplazar al cliente.
     *
     * @param fallbackType la clase declarada en {@code fallback()} ({@code void.class} si no hay)
     * @throws ClientException si no implementa la interfaz del cliente o no es una clase concreta
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
     * Obtiene un componente declarado por clase en la anotación (manejador de errores, interceptores). Con
     * contexto de Spring, usa el bean de ese tipo si hay uno solo y, si no, crea una instancia con inyección
     * de dependencias (puede usar {@code @Autowired} o constructor con beans). Sin contexto, lo instancia con
     * su constructor sin argumentos.
     *
     * @param type la clase del componente
     * @param <C> el tipo del componente
     * @return el componente
     * @throws NoSuchMethodException si no se puede instanciar
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
     * Crea el RestClient. Parte de la request factory y la URL del cliente y después aplica los
     * {@link RestClientCustomizer} de la aplicación en su orden. Así se usa la misma configuración que el
     * {@code RestClient.Builder} de Spring Boot (message converters con el Jackson de la aplicación,
     * observabilidad, interceptores), sin pedir ese bean: Boot lo crea con su propia request factory y su
     * pool, que quedarían sin cerrar.
     *
     * Como los customizers van después, un test puede reemplazar la request factory por un
     * {@code MockRestServiceServer}; el {@code guard} impide que lo haga por accidente un customizer pensado
     * para otros clientes (ver {@link RequestFactoryOverridePolicy}).
     *
     * @param annotation la configuración del cliente
     * @param factory la request factory del cliente
     * @param interceptors los interceptores propios del cliente, que se agregan después de los globales
     * @param guard vigila los reemplazos de la request factory
     * @return el RestClient
     */
    private RestClient buildRestClient(RestHttpClient annotation, HttpComponentsClientHttpRequestFactory factory,
                                       List<ClientHttpRequestInterceptor> interceptors, RequestFactoryGuard guard) {
        RestClient.Builder builder = restClientUtil.restClientBuilder(annotation, factory);
        AutowireCapableBeanFactory beanFactory = beanFactory();
        if (beanFactory instanceof ListableBeanFactory listableBeanFactory) {
            listableBeanFactory.getBeanProvider(RestClientCustomizer.class).orderedStream()
                    .forEach(customizer -> guard.customize(builder, customizer));
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
     * Crea el RestTemplate sobre la request factory del cliente. Si la aplicación tiene un
     * {@link RestTemplateBuilder} (Spring Boot), lo usa para heredar sus message converters, interceptores
     * y customizers; sus {@code clientSettings} globales se descartan para no pisar los timeouts y el SSL
     * propios del cliente.
     *
     * Los customizers del builder se aplican después de la request factory del cliente (así un test puede
     * reemplazarla por un {@code MockRestServiceServer}); el {@code guard} revisa el resultado y restaura la
     * del cliente si el reemplazo no es aceptable. Después se aplican los {@link RestHttpClientCustomizer}.
     *
     * @param annotation la configuración del cliente
     * @param factory la request factory del cliente
     * @param interceptors los interceptores propios del cliente, que se agregan después de los globales
     * @param guard vigila los reemplazos de la request factory
     * @return el RestTemplate
     */
    private RestTemplate buildRestTemplate(RestHttpClient annotation, HttpComponentsClientHttpRequestFactory factory,
                                           List<ClientHttpRequestInterceptor> interceptors,
                                           RequestFactoryGuard guard) {
        AutowireCapableBeanFactory beanFactory = beanFactory();
        RestTemplateBuilder builder = beanFactory instanceof ListableBeanFactory listableBeanFactory
                ? listableBeanFactory.getBeanProvider(RestTemplateBuilder.class).getIfAvailable()
                : null;
        RestTemplate template = builder == null
                ? restClientUtil.configureRestTemplate(factory)
                : builder.clientSettings(HttpClientSettings.defaults()).requestFactory(() -> factory).build();
        guard.guardTemplate(template, factory);
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
     * Obtiene el nombre del cliente que ven los {@link RestHttpClientCustomizer}: el {@code name()} de la
     * anotación o, si está vacío, el nombre simple de la interfaz.
     *
     * @param annotation la configuración del cliente (con los placeholders resueltos)
     * @return el nombre del cliente
     */
    private String clientName(RestHttpClient annotation) {
        return annotation.name().isEmpty() ? clientInterface.getSimpleName() : annotation.name();
    }

    /**
     * Obtiene el bean factory del contexto. Se usa el de {@link ConfigurableApplicationContext} porque está
     * disponible incluso antes del {@code refresh()}.
     *
     * @return el bean factory, o null si no hay contexto
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

    /**
     * Pide crear el proxy al arrancar el contexto, para validar el cliente (URL y métodos) de inmediato.
     *
     * @return siempre true
     */
    @Override
    public boolean isEagerInit() {
        return true;
    }


}
