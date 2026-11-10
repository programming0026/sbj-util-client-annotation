package com.juniverse.client.example.infrastructure.adapter.in.web;

import com.juniverse.client.example.domain.exception.DomainException;
import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.exception.InvalidCredentialsException;
import com.juniverse.client.example.domain.exception.InvalidRequestException;
import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce las excepciones del dominio a respuestas HTTP (RFC 9457, {@code ProblemDetail}).
 *
 * Es el único lugar que conoce la correspondencia entre errores de negocio y códigos de estado.
 *
 * @author Java Universe
 */
@RestControllerAdvice
public class WebExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebExceptionHandler.class);

    /**
     * Traduce una excepción del dominio.
     *
     * @param exception la excepción
     * @return el detalle del problema con el código de estado que corresponde
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException exception) {
        HttpStatus status = switch (exception) {
            case ProductNotFoundException e -> HttpStatus.NOT_FOUND;
            case InvalidRequestException e -> HttpStatus.BAD_REQUEST;
            case InvalidCredentialsException e -> HttpStatus.UNAUTHORIZED;
            case UnauthorizedException e -> HttpStatus.UNAUTHORIZED;
            case ExternalServiceException e -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        if (status.is5xxServerError()) {
            LOG.warn("Request failed with status {}: {}", status.value(), exception.getMessage());
        }
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }

    /**
     * Traduce un parámetro con tipo incorrecto (por ejemplo un identificador no numérico).
     *
     * @param exception la excepción
     * @return el detalle del problema (400)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parameter '" + exception.getName() + "' has an invalid value");
    }
}
