package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.exception.InvalidCredentialsException;
import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.AuthTokens;
import com.juniverse.client.example.domain.model.Credentials;
import com.juniverse.client.example.domain.model.UserProfile;

/**
 * Output port: the identity provider that authenticates users.
 *
 * @author Java Universe
 */
public interface AuthenticationGateway {

    /**
     * Authenticates a user.
     *
     * @param credentials user name and password
     * @return the user and their tokens
     * @throws InvalidCredentialsException if the data is not correct
     * @throws ExternalServiceException if the provider is unavailable
     */
    AuthSession authenticate(Credentials credentials);

    /**
     * Gets the user who owns an access token.
     *
     * @param accessToken the access token
     * @return the user
     * @throws UnauthorizedException if the token is invalid or expired
     * @throws ExternalServiceException if the provider is unavailable
     */
    UserProfile fetchProfile(String accessToken);

    /**
     * Renews the tokens.
     *
     * @param refreshToken the refresh token
     * @return the new tokens
     * @throws UnauthorizedException if the token is invalid or expired
     * @throws ExternalServiceException if the provider is unavailable
     */
    AuthTokens renew(String refreshToken);
}
