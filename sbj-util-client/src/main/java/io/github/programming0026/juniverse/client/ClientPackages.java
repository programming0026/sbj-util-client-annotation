package io.github.programming0026.juniverse.client;

/**
 * El paquete raíz de la librería, escrito como texto en un único lugar.
 *
 * Todo el código y las pruebas que necesiten el nombre del paquete como texto (un prefijo de logger, un filtro
 * de clases, los {@code basePackages} de una prueba) deben usar esta constante y no repetir el literal: así, si
 * el paquete cambia, solo hay que cambiar {@link #BASE}. {@code ClientPackagesTest} falla si la constante deja
 * de coincidir con el paquete real o si alguien vuelve a escribir el literal en otro sitio.
 *
 * Los dos archivos de {@code META-INF/spring} (autoconfiguración y AOT) no admiten constantes y llevan el
 * nombre completo de sus clases; la misma prueba comprueba que siguen apuntando a clases que existen.
 */
public final class ClientPackages {

    /** El paquete raíz de la librería; es una constante de compilación, así que sirve en anotaciones. */
    public static final String BASE = "io.github.programming0026.juniverse.client";

    /**
     * Clase de utilidades: no se instancia.
     */
    private ClientPackages() {
    }
}
