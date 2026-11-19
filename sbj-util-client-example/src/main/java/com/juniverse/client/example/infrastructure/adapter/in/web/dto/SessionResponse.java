package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.AuthSession;

/**
 * Logged-in session in the example's REST API.
 *
 * @param user the user
 * @param tokens the session tokens
 * @author Java Universe
 */
public record SessionResponse(UserResponse user, TokensResponse tokens) {

    /**
     * Converts a domain session.
     *
     * @param session the session
     * @return the response
     */
    public static SessionResponse from(AuthSession session) {
        return new SessionResponse(UserResponse.from(session.user()), TokensResponse.from(session.tokens()));
    }
}
