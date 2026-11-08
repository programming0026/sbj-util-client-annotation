package com.juniverse.client.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación de ejemplo de {@code sbj-util-client}.
 *
 * No hay ningún {@code @EnableRestHttpClients}: la autoconfiguración de la librería registra las interfaces
 * {@code @RestHttpClient} de este paquete y sus subpaquetes.
 */
@SpringBootApplication
public class SbjUtilClientExampleApplication {

    /**
     * Arranca la aplicación.
     *
     * @param args los argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(SbjUtilClientExampleApplication.class, args);
    }
}
