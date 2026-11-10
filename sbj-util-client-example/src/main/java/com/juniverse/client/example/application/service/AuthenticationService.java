package com.juniverse.client.example.application.service;

import com.juniverse.client.example.application.port.in.GetCurrentUserUseCase;
import com.juniverse.client.example.application.port.in.LoginUseCase;
import com.juniverse.client.example.application.port.in.RefreshSessionUseCase;
import com.juniverse.client.example.application.port.out.AuthenticationGateway;
import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.AuthTokens;
import com.juniverse.client.example.domain.model.Credentials;
import com.juniverse.client.example.domain.model.UserProfile;

import java.util.Objects;

/**
 * Casos de uso de autenticación: iniciar sesión, consultar el usuario actual y renovar tokens.
 *
 * Aplica la regla de negocio de que no se llama al proveedor con tokens vacíos.
 *
 * @author Java Universe
 */
public class AuthenticationService implements LoginUseCase, GetCurrentUserUseCase, RefreshSessionUseCase {

    private final AuthenticationGateway authenticationGateway;

    /**
     * Crea el servicio.
     *
     * @param authenticationGateway el puerto del proveedor de identidad
     */
    public AuthenticationService(AuthenticationGateway authenticationGateway) {
        this.authenticationGateway = Objects.requireNonNull(authenticationGateway, "authenticationGateway");
    }

    @Override
    public AuthSession login(Credentials credentials) {
        return authenticationGateway.authenticate(Objects.requireNonNull(credentials, "credentials"));
    }

    @Override
    public UserProfile getCurrentUser(String accessToken) {
        return authenticationGateway.fetchProfile(requireToken(accessToken, "access token"));
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        return authenticationGateway.renew(requireToken(refreshToken, "refresh token"));
    }

    private static String requireToken(String token, String label) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("The " + label + " is missing");
        }
        return token.trim();
    }
}
