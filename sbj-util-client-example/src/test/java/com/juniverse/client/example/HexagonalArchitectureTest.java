package com.juniverse.client.example;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la regla de dependencias de la arquitectura hexagonal revisando los imports del código fuente:
 * las dependencias solo apuntan hacia adentro (infraestructura → aplicación → dominio).
 */
class HexagonalArchitectureTest {

    private static final Path SOURCES = Path.of("src/main/java/com/juniverse/client/example");

    private static final String BASE = "com.juniverse.client.example";

    @Test
    void domainDependsOnNothingOutsideItself() {
        assertNoImports("domain", List.of("org.springframework", "com.fasterxml", "tools.jackson", "jakarta", "io.swagger", "org.springdoc",
                "com.juniverse.client.autoconfigure", BASE + ".application", BASE + ".infrastructure"));
    }

    @Test
    void applicationDependsOnlyOnTheDomain() {
        assertNoImports("application", List.of("org.springframework", "com.fasterxml", "tools.jackson",
                "jakarta", "io.swagger", "org.springdoc", "com.juniverse.client.autoconfigure", BASE + ".infrastructure"));
    }

    @Test
    void inboundAdaptersDoNotKnowOutboundAdapters() {
        assertNoImports("infrastructure/adapter/in", List.of(BASE + ".infrastructure.adapter.out",
                "com.juniverse.client.autoconfigure"));
    }

    @Test
    void outboundAdaptersDoNotKnowInboundAdapters() {
        assertNoImports("infrastructure/adapter/out", List.of(BASE + ".infrastructure.adapter.in"));
    }

    @Test
    void applicationServicesAreNotSpringComponents() {
        assertThat(sourcesOf("application")).allSatisfy(source ->
                assertThat(source.content()).doesNotContain("@Component").doesNotContain("@Service"));
    }

    private static void assertNoImports(String layer, List<String> forbiddenPrefixes) {
        List<Source> sources = sourcesOf(layer);
        assertThat(sources).as("source files of layer %s", layer).isNotEmpty();
        for (Source source : sources) {
            for (String forbidden : forbiddenPrefixes) {
                assertThat(source.content())
                        .as("%s must not import %s", source.path().getFileName(), forbidden)
                        .doesNotContain("import " + forbidden)
                        .doesNotContain("import static " + forbidden);
            }
        }
    }

    private static List<Source> sourcesOf(String layer) {
        try (Stream<Path> files = Files.walk(SOURCES.resolve(layer))) {
            return files.filter(path -> path.toString().endsWith(".java")).map(path -> {
                try {
                    return new Source(path, Files.readString(path));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Source(Path path, String content) {
    }
}
