package com.juniverse.client.handler;

import com.juniverse.client.enums.RequestFactoryOverridePolicy;
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
 * Vigila que los customizers no quiten por accidente la request factory de un cliente (la que lleva su pool
 * de conexiones, timeouts y SSL), según la {@link RequestFactoryOverridePolicy}.
 *
 * Cada vez que un customizer reemplaza la request factory, la librería sabe cuál fue y lo registra en el log.
 */
final class RequestFactoryGuard {

    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RequestFactoryGuard.class);

    /** El nombre del cliente, para los mensajes del log. */
    private final String clientName;

    /** La política que decide qué reemplazos se aceptan. */
    private final RequestFactoryOverridePolicy policy;

    /**
     * Crea el guardián de un cliente.
     *
     * @param clientName el nombre del cliente
     * @param policy la política de reemplazo
     */
    RequestFactoryGuard(String clientName, RequestFactoryOverridePolicy policy) {
        this.clientName = clientName;
        this.policy = policy;
    }

    /**
     * Aplica un {@link RestClientCustomizer} sobre el builder, interceptando sus llamadas a
     * {@code requestFactory(...)}: un reemplazo que la política no acepta se ignora.
     *
     * @param builder el builder, que ya trae la request factory de la librería
     * @param customizer el customizer a aplicar
     */
    void customize(RestClient.Builder builder, RestClientCustomizer customizer) {
        customizer.customize(guarded(builder, "RestClientCustomizer " + customizer.getClass().getName()));
    }

    /**
     * Revisa un {@link RestTemplate} ya construido (con los customizers aplicados): si su request factory ya no
     * es la de la librería y la política no acepta el reemplazo, restaura la de la librería.
     *
     * @param template el RestTemplate
     * @param libraryFactory la request factory de la librería
     */
    void guardTemplate(RestTemplate template, ClientHttpRequestFactory libraryFactory) {
        guardTemplate(template, libraryFactory, "A RestTemplateBuilder customizer");
    }

    /**
     * Igual que {@link #guardTemplate(RestTemplate, ClientHttpRequestFactory)} pero indicando quién pudo
     * hacer el reemplazo, para el log.
     *
     * @param template el RestTemplate
     * @param previousFactory la request factory que tenía antes de la personalización, a la que se vuelve
     *                        si el reemplazo no se acepta
     * @param source quién personalizó el RestTemplate
     */
    void guardTemplate(RestTemplate template, ClientHttpRequestFactory previousFactory, String source) {
        ClientHttpRequestFactory current = currentRequestFactory(template);
        if (current != previousFactory && !accept(source, current)) {
            template.setRequestFactory(previousFactory);
        }
    }

    /**
     * Aplica la personalización de un {@code RestHttpClientCustomizer} sobre el RestTemplate y revisa que no
     * haya reemplazado la request factory sin que la política lo acepte.
     *
     * @param template el RestTemplate
     * @param customizer la personalización
     * @param source quién personaliza, para el log
     */
    void customizeTemplate(RestTemplate template, Consumer<RestTemplate> customizer, String source) {
        ClientHttpRequestFactory before = currentRequestFactory(template);
        customizer.accept(template);
        guardTemplate(template, before, source);
    }

    /**
     * Aplica la personalización de un {@code RestHttpClientCustomizer} sobre el builder de un RestClient,
     * interceptando sus llamadas a {@code requestFactory(...)}.
     *
     * @param builder el builder
     * @param customizer la personalización
     * @param source quién personaliza, para el log
     */
    void customizeBuilder(RestClient.Builder builder, Consumer<RestClient.Builder> customizer, String source) {
        customizer.accept(guarded(builder, source));
    }

    /**
     * Obtiene la request factory del RestTemplate sin el envoltorio de los interceptores: con interceptores,
     * {@code getRequestFactory()} devuelve una factory que los aplica, no la configurada.
     *
     * @param template el RestTemplate
     * @return la request factory configurada
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
     * Decide si se acepta el reemplazo y lo deja en el log.
     *
     * @param source quién lo hizo
     * @param replacement la request factory que reemplaza a la de la librería, o null si se pide la de
     *                    Spring por defecto
     * @return true si se acepta
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
     * Crea un builder que delega en el real pero consulta a la política cuando se llama a
     * {@code requestFactory(...)}. Las llamadas encadenadas y {@code apply(...)} siguen pasando por el
     * builder vigilado.
     *
     * @param delegate el builder real
     * @param source quién personaliza (tipo y clase del customizer), para el log
     * @return el builder vigilado
     */
    @SuppressWarnings("unchecked")
    private RestClient.Builder guarded(RestClient.Builder delegate, String source) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }
            // Incluye requestFactory(null): Spring lo acepta y restablece la request factory por defecto.
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
