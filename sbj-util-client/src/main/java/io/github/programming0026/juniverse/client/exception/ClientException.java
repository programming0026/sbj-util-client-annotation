package io.github.programming0026.juniverse.client.exception;

import java.io.Serial;

/**
 * Excepción de la librería: errores de configuración, de registro, valores requeridos faltantes o
 * tiempo de espera agotado. Los errores HTTP no se envuelven en esta excepción.
 */
public class ClientException extends RuntimeException {
    /**
     * Identificador de versión para serialización.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con un mensaje.
     *
     * @param message el mensaje
     */
    public ClientException(String message) {
        super(message);
    }

    /**
     * Crea la excepción con un mensaje y su causa.
     *
     * @param message el mensaje
     * @param cause la causa
     */
    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Crea la excepción a partir de su causa.
     *
     * @param cause la causa
     */
    public ClientException(Throwable cause) {
        super(cause);
    }
}
