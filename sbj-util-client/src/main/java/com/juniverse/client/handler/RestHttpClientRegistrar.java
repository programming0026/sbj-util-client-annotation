package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.config.ClientConfig;
import com.juniverse.client.exception.ClientException;
import io.github.resilience4j.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registra un bean por cada interfaz anotada con {@link RestHttpClient}.
 *
 * Se activa con {@link EnableRestHttpClients}: busca en los paquetes base las interfaces de nivel
 * superior o anidadas estáticas y registra para cada una un {@link RestHttpClientFactoryBean}.
 */
public class RestHttpClientRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware,
        ResourceLoaderAware {
    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientRegistrar.class);

    /** Nombre del bean marcador que indica que la aplicación usa {@link EnableRestHttpClients}. */
    public static final String ENABLED_MARKER_BEAN = "restHttpClientEnabledMarker";

    /** Bean marcador: existe cuando la aplicación declaró {@link EnableRestHttpClients}. */
    public static final class EnabledMarker implements Serializable {
    }

    /**
     * El entorno para resolver placeholders en {@code basePackages} y {@code name}. Spring lo inyecta
     * antes de registrar; el valor inicial solo se usa si el registrar se crea a mano.
     */
    private Environment environment = new StandardEnvironment();

    /**
     * El cargador de recursos (y de clases) de la aplicación. Spring lo inyecta antes de registrar; el
     * valor inicial solo se usa si el registrar se crea a mano.
     */
    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    /**
     * Recibe el entorno de Spring.
     *
     * @param environment el entorno
     */
    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    /**
     * Recibe el cargador de recursos de Spring; se usa para escanear y cargar las interfaces con el
     * class loader de la aplicación (por ejemplo, el de Spring Boot DevTools).
     *
     * @param resourceLoader el cargador de recursos
     */
    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Busca los clientes y registra sus definiciones de bean. Los argumentos {@code CircuitBreakerUtil} y
     * {@code RestClientUtil} del constructor del factory bean son los beans de {@link ClientConfig},
     * referenciados por sus nombres con prefijo ({@link ClientConfig#CIRCUIT_BREAKER_UTIL_BEAN} y
     * {@link ClientConfig#REST_CLIENT_UTIL_BEAN}).
     *
     * Si un cliente ya está registrado con el mismo nombre (por ejemplo, porque dos
     * {@code @EnableRestHttpClients} escanean el mismo paquete), se omite; si el nombre lo usa otro bean,
     * se lanza un error en vez de sobrescribirlo.
     *
     * @param metadata los metadatos de la clase anotada con {@link EnableRestHttpClients}
     * @param registry el registro de definiciones de bean
     * @throws ClientException si una interfaz no se puede cargar o registrar (por ejemplo, nombre duplicado)
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(
                EnableRestHttpClients.class.getName());

        String[] basePackages = (String[]) attrs.get("basePackages");

        if (basePackages == null || basePackages.length == 0) {
            String className = metadata.getClassName();
            String basePackage = className.substring(0, className.lastIndexOf('.'));
            basePackages = new String[]{basePackage};
            LOG.info("No basePackages specified, using: {}", basePackage);
        }


        if (!registry.containsBeanDefinition(ENABLED_MARKER_BEAN)) {
            registry.registerBeanDefinition(ENABLED_MARKER_BEAN, new RootBeanDefinition(EnabledMarker.class));
        }
        registerClients(basePackages, registry);
    }

    /**
     * Busca los clientes en los paquetes indicados y registra un {@link RestHttpClientFactoryBean} por cada uno.
     *
     * @param basePackages los paquetes donde buscar (admiten placeholders)
     * @param registry el registro de definiciones de bean
     * @throws ClientException si una interfaz no se puede cargar o registrar (por ejemplo, nombre duplicado)
     */
    protected void registerClients(String[] basePackages, BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(
                            AnnotatedBeanDefinition beanDefinition) {
                        AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
                        return annotationMetadata.isInterface()
                                && annotationMetadata.isIndependent();
                    }
                };

        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestHttpClient.class));
        List<String> registeredClients = new ArrayList<>();
        for (String declaredPackage : basePackages) {
            String basePackage = environment.resolveRequiredPlaceholders(declaredPackage);
            LOG.debug("Scanning package: {}", basePackage);
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            LOG.info("Found {} in {}", candidates.size(), basePackage);

            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                LOG.debug("Processing: {}", className);

                try {
                    Class<?> clientInterface =
                            ClassUtils.forName(className, resourceLoader.getClassLoader());

                    if (!clientInterface.isAnnotationPresent(RestHttpClient.class)) {
                        LOG.warn("The interface {} does not have @RestHttpClient", className);
                    } else {
                        RestHttpClient annotation =
                                clientInterface.getAnnotation(RestHttpClient.class);
                        String beanName = determineBeanName(clientInterface, annotation);

                        if (registry.containsBeanDefinition(beanName)) {
                            BeanDefinition existing = registry.getBeanDefinition(beanName);
                            Object existingObjectType =
                                    existing.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);

                            if (clientInterface.equals(existingObjectType)) {
                                LOG.debug(
                                        "Client {} already registered as '{}'",
                                        className,
                                        beanName);
                            } else {
                                throw new ClientException(
                                        "Duplicate bean name '"
                                                + beanName
                                                + "' for client "
                                                + className
                                                + "; set a unique @RestHttpClient(name = ...)");
                            }
                        } else {
                            LOG.info(
                                    "Registering bean: {} for interface: {}",
                                    beanName,
                                    className);

                            BeanDefinitionBuilder definition =
                                    BeanDefinitionBuilder.genericBeanDefinition(
                                            RestHttpClientFactoryBean.class);

                            definition.addConstructorArgValue(clientInterface);
                            definition.addConstructorArgReference(
                                    ClientConfig.CIRCUIT_BREAKER_UTIL_BEAN);
                            definition.addConstructorArgReference(
                                    ClientConfig.REST_CLIENT_UTIL_BEAN);

                            AbstractBeanDefinition beanDefinition =
                                    definition.getBeanDefinition();
                            beanDefinition.setAttribute(
                                    FactoryBean.OBJECT_TYPE_ATTRIBUTE,
                                    clientInterface);

                            registry.registerBeanDefinition(beanName, beanDefinition);
                            registeredClients.add(beanName);

                            LOG.info("Bean '{}' successfully registered", beanName);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    LOG.error("The class could not be loaded: {}", className, e);
                    throw new ClientException("Cannot load client interface: " + className, e);
                } catch (ClientException e) {
                    throw e;
                } catch (Exception e) {
                    LOG.error("Error registering bean for: {}", className, e);
                    throw new ClientException("Error registering client: " + className, e);
                }
            }
        }

        LOG.info("Total number of registered clients: {}", registeredClients.size());
    }

    /**
     * Determina el nombre del bean: el {@code name} de la anotación (con sus placeholders resueltos) o,
     * si está vacío, el nombre simple de la interfaz con la primera letra en minúscula.
     *
     * @param clientInterface la interfaz del cliente
     * @param annotation la anotación del cliente
     * @return el nombre del bean
     */
    private String determineBeanName(Class<?> clientInterface, RestHttpClient annotation) {

        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return environment.resolveRequiredPlaceholders(annotation.name());
        }

        String simpleName = clientInterface.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
