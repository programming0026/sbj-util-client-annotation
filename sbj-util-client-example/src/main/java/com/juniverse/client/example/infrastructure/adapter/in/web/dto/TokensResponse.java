package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.AuthTokens;

/**
 * Tokens in the example's REST API.
 *
 * @param accessToken access token
 * @param refreshToken refresh token
 * @author Java Universe
 */
public record TokensResponse(String accessToken, String refreshToken) {

    /**
     * Converts the domain tokens.
     *
     * @param tokens the tokens
     * @return the response
     */
    public static TokensResponse from(AuthTokens tokens) {
        return new TokensResponse(tokens.accessToken(), tokens.refreshToken());
    }

    /** Tokens must never appear in logs. */
    @Override
    public String toString() {
        return "TokensResponse[tokens=***]";
    }
}
