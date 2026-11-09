package io.github.programming0026.juniverse.client;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El nombre del paquete raíz está en {@link ClientPackages#BASE} y en ningún otro texto del código: si el paquete
 * cambia, solo se edita la constante (y los archivos de {@code META-INF/spring}, que no admiten constantes).
 */
class ClientPackagesTest {

    /** Un literal de texto que contiene el nombre del paquete raíz. */
    private static final Pattern PACKAGE_IN_STRING = Pattern.compile("\"[^\"\\n]*"
            + Pattern.quote(ClientPackages.BASE) + "[^\"\\n]*\"");

    @Test
    void theConstantMatchesTheRealPackage() {
        assertThat(ClientPackages.class.getPackageName()).isEqualTo(ClientPackages.BASE);
    }

    @Test
    void thePublicApiLivesUnderTheBasePackage() {
        assertThat(RestHttpClient.class.getPackageName()).startsWith(ClientPackages.BASE + ".");
    }

    @Test
    void noSourceFileRepeatsThePackageNameAsText() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String root : List.of("src/main/java", "src/test/java")) {
            try (Stream<Path> files = Files.walk(Paths.get(root))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    if (file.getFileName().toString().equals("ClientPackages.java")
                            || file.getFileName().toString().equals("ClientPackagesTest.java")) {
                        continue;
                    }
                    findLiterals(file, offenders);
                }
            }
        }
        assertThat(offenders)
                .as("use ClientPackages.BASE instead of writing the package name in a string")
                .isEmpty();
    }

    private static void findLiterals(Path file, List<String> offenders) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                continue;
            }
            Matcher matcher = PACKAGE_IN_STRING.matcher(line);
            if (matcher.find()) {
                offenders.add(file + ":" + (i + 1) + " " + matcher.group());
            }
        }
    }

    @Test
    void theSpringResourceFilesPointToClassesThatExist() throws Exception {
        List<String> classNames = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/META-INF/spring/aot.factories")) {
            Properties factories = new Properties();
            factories.load(in);
            factories.values().forEach(value -> classNames.add(value.toString().trim()));
        }
        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            new String(in.readAllBytes()).lines().map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(classNames::add);
        }

        assertThat(classNames).isNotEmpty();
        for (String className : classNames) {
            assertThat(Class.forName(className)).as(className).isNotNull();
            assertThat(className).startsWith(ClientPackages.BASE + ".");
        }
    }
}
