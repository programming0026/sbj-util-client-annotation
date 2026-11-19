package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.config.ClientConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
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
 * Registers a bean for each interface annotated with {@link RestHttpClient}.
 * It is activated with {@link EnableRestHttpClients}: it searches the base packages for top-level
 * or static nested interfaces and registers a {@link RestHttpClientFactoryBean} for each one.
 *
 * @author Java Universe
 */
public class RestHttpClientRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware,
        ResourceLoaderAware {
    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientRegistrar.class);

    /** Name of the marker bean that indicates the application uses {@link EnableRestHttpClients}. */
    public static final String ENABLED_MARKER_BEAN = "restHttpClientEnabledMarker";

    /** Marker bean: it exists when the application declared {@link EnableRestHttpClients}. */
    public static final class EnabledMarker implements Serializable {
    }

    /**
     * The environment used to resolve placeholders in {@code basePackages} and {@code name}. Spring injects it
     * before registering; the initial value is only used if the registrar is created by hand.
     */
    private Environment environment = new StandardEnvironment();

    /**
     * The application's resource (and class) loader. Spring injects it before registering; the
     * initial value is only used if the registrar is created by hand.
     */
    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    /**
     * Receives the Spring environment.
     *
     * @param environment the environment
     */
    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    /**
     * Receives Spring's resource loader; it is used to scan and load the interfaces with the
     * application's class loader (for example, that of Spring Boot DevTools).
     *
     * @param resourceLoader the resource loader
     */
    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Finds the clients and registers their bean definitions. The {@code CircuitBreakerUtil} and
     * {@code RestClientUtil} arguments of the factory bean constructor are the beans of {@link ClientConfig},
     * referenced by their prefixed names ({@link ClientConfig#CIRCUIT_BREAKER_UTIL_BEAN} and
     * {@link ClientConfig#REST_CLIENT_UTIL_BEAN}).
     * If a client is already registered with the same name (for example, because two
     * {@code @EnableRestHttpClients} scan the same package), it is skipped; if the name is used by another bean,
     * an error is thrown instead of overwriting it.
     *
     * @param metadata the metadata of the class annotated with {@link EnableRestHttpClients}
     * @param registry the bean definition registry
     * @throws ClientException if an interface cannot be loaded or registered (for example, duplicate name)
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
     * Searches for the clients in the given packages and registers a {@link RestHttpClientFactoryBean} for each.
     *
     * @param basePackages the packages to search (they accept placeholders)
     * @param registry the bean definition registry
     * @throws ClientException if an interface cannot be loaded or registered (for example, duplicate name)
     */
    protected void registerClients(String[] basePackages, BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner = createClientScanner();
        List<String> registeredClients = new ArrayList<>();

        for (String declaredPackage : basePackages) {
            String basePackage = environment.resolveRequiredPlaceholders(declaredPackage);
            LOG.debug("Scanning package: {}", basePackage);
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            LOG.info("Found {} in {}", candidates.size(), basePackage);

            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                LOG.debug("Processing: {}", className);
                processCandidate(className, registry, registeredClients);
            }
        }
        LOG.info("Total number of registered clients: {}", registeredClients.size());
    }

    private ClassPathScanningCandidateComponentProvider createClientScanner() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(
                            AnnotatedBeanDefinition beanDefinition) {
                        AnnotationMetadata metadata = beanDefinition.getMetadata();
                        return metadata.isInterface() && metadata.isIndependent();
                    }
                };

        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestHttpClient.class));
        return scanner;
    }

    private void processCandidate(String className,
            BeanDefinitionRegistry registry,
            List<String> registeredClients) {
        try {
            Class<?> clientInterface =
                    ClassUtils.forName(className, resourceLoader.getClassLoader());
            registerClientIfAnnotated(clientInterface, className, registry, registeredClients);
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

    private void registerClientIfAnnotated(
            Class<?> clientInterface,
            String className,
            BeanDefinitionRegistry registry,
            List<String> registeredClients) {
        if (!clientInterface.isAnnotationPresent(RestHttpClient.class)) {
            LOG.warn("The interface {} does not have @RestHttpClient", className);
            return;
        }

        RestHttpClient annotation = clientInterface.getAnnotation(RestHttpClient.class);
        String beanName = determineBeanName(clientInterface, annotation);

        if (registry.containsBeanDefinition(beanName)) {
            validateExistingClient(registry.getBeanDefinition(beanName), clientInterface, beanName, className);
            return;
        }

        registerBean(clientInterface, className, beanName, registry);
        registeredClients.add(beanName);
    }

    private void validateExistingClient(
            BeanDefinition existing,
            Class<?> clientInterface,
            String beanName,
            String className) {
        Object existingObjectType = existing.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);

        if (clientInterface.equals(existingObjectType)) {
            LOG.debug("Client {} already registered as '{}'", className, beanName);
            return;
        }

        throw new ClientException(
                "Duplicate bean name '"
                        + beanName
                        + "' for client "
                        + className
                        + "; set a unique @RestHttpClient(name = ...)");
    }

    private void registerBean(
            Class<?> clientInterface,
            String className,
            String beanName,
            BeanDefinitionRegistry registry) {
        LOG.info("Registering bean: {} for interface: {}", beanName, className);

        BeanDefinitionBuilder definition =
                BeanDefinitionBuilder.genericBeanDefinition(RestHttpClientFactoryBean.class);

        definition.addConstructorArgValue(clientInterface);
        definition.addConstructorArgReference(ClientConfig.CIRCUIT_BREAKER_UTIL_BEAN);
        definition.addConstructorArgReference(ClientConfig.REST_CLIENT_UTIL_BEAN);

        AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, clientInterface);

        registry.registerBeanDefinition(beanName, beanDefinition);
        LOG.info("Bean '{}' successfully registered", beanName);
    }

    /**
     * Determines the bean name: the {@code name} of the annotation (with its placeholders resolved) or,
     * if empty, the simple name of the interface with the first letter in lowercase.
     *
     * @param clientInterface the client interface
     * @param annotation the client annotation
     * @return the bean name
     */
    private String determineBeanName(Class<?> clientInterface, RestHttpClient annotation) {

        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return environment.resolveRequiredPlaceholders(annotation.name());
        }

        String simpleName = clientInterface.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
