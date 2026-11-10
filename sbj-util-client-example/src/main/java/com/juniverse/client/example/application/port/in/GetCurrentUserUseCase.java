package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.UserProfile;

/**
 * Caso de uso: consultar el usuario dueño de un token de acceso.
 *
 * @author Java Universe
 */
public interface GetCurrentUserUseCase {

    /**
     * Obtiene el usuario actual.
     *
     * @param accessToken el token de acceso
     * @return el usuario
     * @throws UnauthorizedException si el token falta, es inválido o expiró
     */
    UserProfile getCurrentUser(String accessToken);
}
