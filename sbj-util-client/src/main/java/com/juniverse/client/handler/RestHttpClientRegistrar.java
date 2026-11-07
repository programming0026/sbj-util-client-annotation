package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.exception.ClientException;
import io.github.resilience4j.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

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
public class RestHttpClientRegistrar implements ImportBeanDefinitionRegistrar {
    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientRegistrar.class);
    /**
     * Busca los clientes y registra sus definiciones de bean. El argumento {@code RestClientUtil} del
     * constructor del factory bean se resuelve por autowiring.
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

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
                        return annotationMetadata.isInterface()
                                && annotationMetadata.isIndependent();
                    }
                };

        scanner.addIncludeFilter(new AnnotationTypeFilter(RestHttpClient.class));
        List<String> registeredClients = new ArrayList<>();
        for (String basePackage : basePackages) {
            LOG.debug("Scanning package: {}", basePackage);
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

            LOG.info("Found {} in {}", candidates.size(), basePackage);

            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                LOG.debug("Processing: {}", className);

                try {
                    Class<?> clientInterface = Class.forName(className);

                    if (!clientInterface.isAnnotationPresent(RestHttpClient.class)) {
                        LOG.warn("The interface {} does not have @RestHttpClient", className);
                        continue;
                    }

                    RestHttpClient annotation = clientInterface.getAnnotation(RestHttpClient.class);
                    String beanName = determineBeanName(clientInterface, annotation);

                    LOG.info("Registering bean: {} for interface: {}", beanName, className);

                    BeanDefinitionBuilder definition = BeanDefinitionBuilder
                            .genericBeanDefinition(RestHttpClientFactoryBean.class);

                    definition.addConstructorArgValue(clientInterface);
                    definition.addConstructorArgReference("resilience4jConfiguration");

                    AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();

                    registry.registerBeanDefinition(beanName, beanDefinition);
                    registeredClients.add(beanName);

                    LOG.info("Bean '{}' successfully registered", beanName);

                } catch (ClassNotFoundException e) {
                    LOG.error("The class could not be loaded: {}", className, e);
                    throw new ClientException("Cannot load client interface: " + className, e);
                } catch (Exception e) {
                    LOG.error("Error registering bean for: {}", className, e);
                    throw new ClientException("Error registering client: " + className, e);
                }
            }
        }
        LOG.info("Total number of registered clients: {}", registeredClients.size());
    }

    /**
     * Determina el nombre del bean: el {@code name} de la anotación o, si está vacío, el nombre simple
     * de la interfaz con la primera letra en minúscula.
     *
     * @param clientInterface la interfaz del cliente
     * @param annotation la anotación del cliente
     * @return el nombre del bean
     */
    private String determineBeanName(Class<?> clientInterface, RestHttpClient annotation) {

        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }

        String simpleName = clientInterface.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
