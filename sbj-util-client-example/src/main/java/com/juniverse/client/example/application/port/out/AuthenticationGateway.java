package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.exception.InvalidCredentialsException;
import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.AuthTokens;
import com.juniverse.client.example.domain.model.Credentials;
import com.juniverse.client.example.domain.model.UserProfile;

/** Puerto de salida: el proveedor de identidad que autentica a los usuarios. */
public interface AuthenticationGateway {

    /**
     * Autentica a un usuario.
     *
     * @param credentials usuario y contraseña
     * @return el usuario y sus tokens
     * @throws InvalidCredentialsException si los datos no son correctos
     * @throws ExternalServiceException si el proveedor no está disponible
     */
    AuthSession authenticate(Credentials credentials);

    /**
     * Obtiene el usuario dueño de un token de acceso.
     *
     * @param accessToken el token de acceso
     * @return el usuario
     * @throws UnauthorizedException si el token es inválido o expiró
     * @throws ExternalServiceException si el proveedor no está disponible
     */
    UserProfile fetchProfile(String accessToken);

    /**
     * Renueva los tokens.
     *
     * @param refreshToken el token de renovación
     * @return los tokens nuevos
     * @throws UnauthorizedException si el token es inválido o expiró
     * @throws ExternalServiceException si el proveedor no está disponible
     */
    AuthTokens renew(String refreshToken);
}
