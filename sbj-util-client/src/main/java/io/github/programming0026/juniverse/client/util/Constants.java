package io.github.programming0026.juniverse.client.util;

/**
 * El paquete raíz de la librería, escrito como texto en un único lugar.
 * Los dos archivos de {@code META-INF/spring} (autoconfiguración y AOT) no admiten constantes y llevan el
 * nombre completo de sus clases; la misma prueba comprueba que siguen apuntando a clases que existen.
 *
 * @author Java Universe
 */
public final class Constants {

    /** El paquete raíz de la librería; es una constante de compilación, así que sirve en anotaciones. */
    public static final String BASE = "io.github.programming0026.juniverse.client";

    /**
     * Clase de utilidades: no se instancia.
     */
    private Constants() {
    }
}
