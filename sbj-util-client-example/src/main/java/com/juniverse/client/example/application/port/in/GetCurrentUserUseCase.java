package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.UserProfile;

/**
 * Use case: get the user who owns an access token.
 *
 * @author Java Universe
 */
public interface GetCurrentUserUseCase {

    /**
     * Gets the current user.
     *
     * @param accessToken the access token
     * @return the user
     * @throws UnauthorizedException if the token is missing, invalid or expired
     */
    UserProfile getCurrentUser(String accessToken);
}
