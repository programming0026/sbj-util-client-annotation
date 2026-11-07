package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.exception.ClientException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Class RestHttpClientRegistrar.
 */
public class RestHttpClientRegistrar implements ImportBeanDefinitionRegistrar {
    
    /**
     * Register bean definitions.
     *
     * @param metadata the metadata
     * @param registry the registry
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
            //log.info("No se especificaron basePackages, usando: {}", basePackage);
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
            //log.debug("Scanning package: {}", basePackage);
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

            //log.info("Found {} in {}", candidates.size(), basePackage);

            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                //log.debug("Processing: {}", className);

                try {
                    Class<?> clientInterface = Class.forName(className);

                    if (!clientInterface.isAnnotationPresent(RestHttpClient.class)) {
                        //log.warn("The interface {} does not have @RestHttpClient", className);
                        continue;
                    }

                    RestHttpClient annotation = clientInterface.getAnnotation(RestHttpClient.class);
                    String beanName = determineBeanName(clientInterface, annotation);

                    //log.info("Registering bean: {} for interface: {}", beanName, className);

                    BeanDefinitionBuilder definition = BeanDefinitionBuilder
                            .genericBeanDefinition(RestHttpClientFactoryBean.class);

                    definition.addConstructorArgValue(clientInterface);
                    definition.addConstructorArgReference("resilience4jConfiguration");

                    AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();

                    registry.registerBeanDefinition(beanName, beanDefinition);
                    registeredClients.add(beanName);

                    //log.info("Bean '{}' successfully registered", beanName);

                } catch (ClassNotFoundException e) {
                    //log.error("The class could not be loaded: {}", className, e);
                    throw new ClientException("Cannot load client interface: " + className, e);
                } catch (Exception e) {
                    //log.error("Error registering bean for: {}", className, e);
                    throw new ClientException("Error registering client: " + className, e);
                }
            }
        }
        //log.info("Total number of registered client: {}", registeredClients.size());
    }

    /**
     * Determina el nombre del bean basado en la anotación o el nombre de la interfaz.
     *
     * @param clientInterface The client.
     * @param annotation The annotation.
     * @return The String
     */
    private String determineBeanName(Class<?> clientInterface, RestHttpClient annotation) {

        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }

        String simpleName = clientInterface.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
