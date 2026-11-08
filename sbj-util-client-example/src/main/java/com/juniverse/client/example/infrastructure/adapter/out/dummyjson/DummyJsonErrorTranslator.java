package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import com.juniverse.client.example.domain.exception.DomainException;
import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Traduce los errores técnicos de los clientes de DummyJSON a excepciones del dominio.
 *
 * Los adaptadores tratan primero los casos con significado de negocio (un 404 es "no existe", un 400 al
 * iniciar sesión es "credenciales inválidas") y delegan aquí el resto, de modo que ninguna excepción de
 * Spring, de la librería o de Resilience4j sale del adaptador.
 */
@Component
public class DummyJsonErrorTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(DummyJsonErrorTranslator.class);

    /**
     * Traduce un error técnico.
     *
     * <ul>
     *   <li>Un 4xx que el servicio externo rechazó: {@link InvalidRequestException}.</li>
     *   <li>Cualquier otro (5xx, red, timeout, circuito abierto): {@link ExternalServiceException}.</li>
     * </ul>
     *
     * @param operation qué se intentaba hacer, para el mensaje (por ejemplo {@code list products})
     * @param error el error técnico
     * @return la excepción del dominio
     */
    public DomainException translate(String operation, RuntimeException error) {
        if (error instanceof DomainException domainException) {
            return domainException;
        }
        if (error instanceof HttpClientErrorException clientError) {
            LOG.warn("DummyJSON rejected '{}' with status {}", operation, clientError.getStatusCode().value());
            return new InvalidRequestException("DummyJSON rejected the request to " + operation + " (status "
                    + clientError.getStatusCode().value() + ")");
        }
        LOG.warn("DummyJSON is unavailable while trying to {}: {}", operation, error.toString());
        return new ExternalServiceException("DummyJSON is unavailable while trying to " + operation, error);
    }
}
