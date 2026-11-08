package com.juniverse.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de código que el compilador no vigila: se escribe en el log con SLF4J, nunca en la consola.
 */
class CodingRulesTest {

    private static final List<String> FORBIDDEN = List.of("System" + ".out", "System" + ".err", "printStack" + "Trace(");

    @Test
    void sourcesLogWithSlf4jInsteadOfPrintingToTheConsole() {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.getFileName().toString().equals("CodingRulesTest.java"))
                        .forEach(path -> collectViolations(path, violations));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        assertThat(violations).as("use an SLF4J Logger instead").isEmpty();
    }

    private static void collectViolations(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                for (String forbidden : FORBIDDEN) {
                    if (lines.get(i).contains(forbidden)) {
                        violations.add(path + ":" + (i + 1) + " uses " + forbidden);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
