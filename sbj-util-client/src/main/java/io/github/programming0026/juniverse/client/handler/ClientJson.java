package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.config.JsonMapperConfigurer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Crea el conversor JSON (Jackson 3) propio de un cliente a partir de su {@link JsonMapperConfigurer}.
 */
final class ClientJson {

    /**
     * Instancia.
     */
    private ClientJson() {
    }

    /**
     * Crea el conversor: parte del {@link JsonMapper} de la aplicación (si hay uno solo) o del mapper por defecto
     * de Spring, y le aplica el configurador.
     *
     * @param beanFactory el bean factory, o {@code null} fuera de Spring
     * @param configurer el configurador del cliente
     * @return el conversor JSON del cliente
     */
    static JacksonJsonHttpMessageConverter converter(ListableBeanFactory beanFactory,
                                                     JsonMapperConfigurer configurer) {
        JsonMapper base = beanFactory == null ? null : beanFactory.getBeanProvider(JsonMapper.class).getIfUnique();
        JsonMapper.Builder builder = base == null
                ? new JacksonJsonHttpMessageConverter().getMapper().rebuild() : base.rebuild();
        configurer.configure(builder);
        return new JacksonJsonHttpMessageConverter(builder.build());
    }

    /**
     * Sustituye en la lista el conversor JSON de Jackson por el del cliente; si no había, lo pone el primero.
     *
     * @param converters la lista de conversores del cliente (modificable)
     * @param json el conversor JSON del cliente
     */
    static void install(List<HttpMessageConverter<?>> converters, HttpMessageConverter<?> json) {
        boolean replaced = false;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof JacksonJsonHttpMessageConverter) {
                converters.set(i, json);
                replaced = true;
            }
        }
        if (!replaced) {
            converters.add(0, json);
        }
    }
}
