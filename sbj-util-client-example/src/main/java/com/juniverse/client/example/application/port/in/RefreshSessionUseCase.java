package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.AuthTokens;

/**
 * Use case: renew the tokens of a session.
 *
 * @author Java Universe
 */
public interface RefreshSessionUseCase {

    /**
     * Renews the tokens.
     *
     * @param refreshToken the refresh token
     * @return the new tokens
     * @throws UnauthorizedException if the token is missing, invalid or expired
     */
    AuthTokens refresh(String refreshToken);
}
