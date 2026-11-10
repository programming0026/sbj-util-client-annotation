package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.AuthTokens;

/**
 * Caso de uso: renovar los tokens de una sesión.
 *
 * @author Java Universe
 */
public interface RefreshSessionUseCase {

    /**
     * Renueva los tokens.
     *
     * @param refreshToken el token de renovación
     * @return los tokens nuevos
     * @throws UnauthorizedException si el token falta, es inválido o expiró
     */
    AuthTokens refresh(String refreshToken);
}
