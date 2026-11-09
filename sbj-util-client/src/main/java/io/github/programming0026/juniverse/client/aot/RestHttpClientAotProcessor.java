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
 * Procesador AOT: durante la compilación (Spring AOT / imagen nativa) busca los clientes registrados y
 * declara sus hints de proxy y reflexión. Se registra en {@code META-INF/spring/aot.factories}.
 */
public class RestHttpClientAotProcessor implements BeanFactoryInitializationAotProcessor {

    /**
     * Crea la contribución con los hints de todos los clientes del contexto.
     *
     * @param beanFactory el bean factory ya con todas las definiciones
     * @return la contribución, o {@code null} si no hay clientes
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
     * Obtiene la interfaz de una definición creada por el registrador, o {@code null} si es otro bean.
     *
     * @param definition la definición del bean
     * @return la interfaz del cliente o {@code null}
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
