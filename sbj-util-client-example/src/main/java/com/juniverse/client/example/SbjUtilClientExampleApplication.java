package com.juniverse.client.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Example application of {@code sbj-util-client}.
 *
 * There is no {@code @EnableRestHttpClients}: the library's auto-configuration registers the
 * {@code @RestHttpClient} interfaces of this package and its subpackages.
 *
 * @author Java Universe
 */
@SpringBootApplication
public class SbjUtilClientExampleApplication {

    /**
     * Starts the application.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SbjUtilClientExampleApplication.class, args);
    }
}
