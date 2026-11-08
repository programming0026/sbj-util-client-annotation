package com.juniverse.client.boot;

import com.juniverse.client.handler.placeholder.PlaceholderClients;
import com.juniverse.client.handler.scan.ScannedClients;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Autoconfiguración de Spring Boot: escaneo automático de clientes y métricas. */
class RestHttpClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestHttpClientAutoConfiguration.class,
                    RestHttpClientMetricsAutoConfiguration.class));

    /** Simula una aplicación cuyo {@code @SpringBootApplication} está en el paquete de los clientes de prueba. */
    private final ApplicationContextRunner bootApplication = runner.withInitializer(context ->
            AutoConfigurationPackages.register((BeanDefinitionRegistry) context.getBeanFactory(),
                    "com.juniverse.client.handler.scan"));

    @Test
    void registersClientsFromApplicationPackagesWithoutEnableAnnotation() {
        bootApplication.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("namedClient")).isInstanceOf(ScannedClients.NamedClient.class);
            assertThat(context).hasSingleBean(ScannedClients.DerivedNameClient.class);
        });
    }

    @Test
    void enableAnnotationTakesPrecedenceOverAutoScan() {
        bootApplication
                .withUserConfiguration(PlaceholderClients.Config.class)
                .withPropertyValues("test.clients-package=com.juniverse.client.handler.placeholder",
                        "test.client-name=items", "test.base-url=http://localhost", "test.prefix=/v1",
                        "test.api-key=k", "test.default-page=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("items");
                    // Los paquetes de la aplicación no se escanean: manda @EnableRestHttpClients.
                    assertThat(context).doesNotHaveBean(ScannedClients.NamedClient.class);
                });
    }

    @Test
    void autoScanCanBeDisabled() {
        bootApplication.withPropertyValues("rest-http-client.auto-scan.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ScannedClients.NamedClient.class);
                    assertThat(context).doesNotHaveBean(MeterBinder.class);
                });
    }

    @Test
    void doesNothingWithoutApplicationPackages() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ScannedClients.NamedClient.class);
        });
    }

    @Test
    void publishesResilienceMetricsOfEveryClient() {
        bootApplication.run(context -> {
            MeterRegistry registry = new SimpleMeterRegistry();

            context.getBean(MeterBinder.class).bindTo(registry);

            assertThat(registry.find("resilience4j.retry.calls").tag("name", "namedClient").meters()).isNotEmpty();
            assertThat(registry.find("resilience4j.retry.calls").tag("name", "DerivedNameClient").meters())
                    .isNotEmpty();
        });
    }

    @Test
    void metricsAreSkippedWithoutMicrometer() {
        bootApplication.withClassLoader(new FilteredClassLoader(MeterBinder.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ScannedClients.DerivedNameClient.class);
                    assertThat(context).doesNotHaveBean("restHttpClientResilienceMetrics");
                });
    }
}
