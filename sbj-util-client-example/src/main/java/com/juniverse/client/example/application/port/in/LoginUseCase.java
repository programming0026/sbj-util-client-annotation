package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.InvalidCredentialsException;
import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.Credentials;

/**
 * Caso de uso: iniciar sesión.
 *
 * @author Java Universe
 */
public interface LoginUseCase {

    /**
     * Inicia sesión.
     *
     * @param credentials usuario y contraseña
     * @return el usuario y sus tokens
     * @throws InvalidCredentialsException si los datos no son correctos
     */
    AuthSession login(Credentials credentials);
}
