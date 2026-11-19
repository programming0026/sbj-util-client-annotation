package io.github.programming0026.juniverse.client.aot;

import io.github.programming0026.juniverse.client.handler.RestHttpClientFactoryBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;

import java.util.ArrayList;
import java.util.List;

/**
 * AOT processor: at build time (Spring AOT / native image) it finds the registered clients and
 * declares their proxy and reflection hints. It is registered in {@code META-INF/spring/aot.factories}.
 *
 * @author Java Universe
 */
public class RestHttpClientAotProcessor implements BeanFactoryInitializationAotProcessor {

    /**
     * Creates the contribution with the hints of every client in the context.
     *
     * @param beanFactory the bean factory with all the definitions already registered
     * @return the contribution, or {@code null} if there are no clients
     */
    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        List<Class<?>> clients = new ArrayList<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            Class<?> clientInterface = clientInterface(beanFactory.getBeanDefinition(name));
            if (clientInterface != null) {
                clients.add(clientInterface);
            }
        }
        if (clients.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) ->
                clients.forEach(client -> RestHttpClientRuntimeHints.register(generationContext.getRuntimeHints(),
                        client));
    }

    /**
     * Gets the interface of a definition created by the registrar, or {@code null} if it is another bean.
     *
     * @param definition the bean definition
     * @return the client interface or {@code null}
     */
    static Class<?> clientInterface(BeanDefinition definition) {
        if (!RestHttpClientFactoryBean.class.getName().equals(definition.getBeanClassName())) {
            return null;
        }
        ConstructorArgumentValues.ValueHolder holder =
                definition.getConstructorArgumentValues().getIndexedArgumentValue(0, null);
        if (holder != null && holder.getValue() instanceof Class<?> type) {
            return type;
        }
        Object attribute = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        return attribute instanceof Class<?> type ? type : null;
    }
}
