package com.juniverse.client.example.domain.exception;

/** El servicio externo no está disponible: falla de red, timeout, error 5xx o circuito abierto. */
public class ExternalServiceException extends DomainException {

    /**
     * Crea la excepción.
     *
     * @param message qué operación falló
     * @param cause la causa técnica
     */
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
