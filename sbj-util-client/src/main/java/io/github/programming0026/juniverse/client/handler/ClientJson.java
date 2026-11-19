package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.config.JsonMapperConfigurer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Creates the JSON (Jackson 3) converter specific to a client from its {@link JsonMapperConfigurer}.
 *
 * @author Java Universe
 */
final class ClientJson {

    /**
     * Instance.
     */
    private ClientJson() {
    }

    /**
     * Creates the converter: it starts from the application's {@link JsonMapper} (if there is a single one) or
     * from Spring's default mapper, and applies the configurer to it.
     *
     * @param beanFactory the bean factory, or {@code null} outside Spring
     * @param configurer the client's configurer
     * @return the client's JSON converter
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
     * Replaces the Jackson JSON converter in the list with the client's one; if there was none, it puts it first.
     *
     * @param converters the client's converter list (modifiable)
     * @param json the client's JSON converter
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
