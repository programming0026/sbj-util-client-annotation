package com.juniverse.client.example.domain.model;

/**
 * Pair of tokens of a session.
 *
 * @param accessToken short-lived access token
 * @param refreshToken token to obtain a new access token
 * @author Java Universe
 */
public record AuthTokens(String accessToken, String refreshToken) {

    /** Tokens must never appear in logs. */
    @Override
    public String toString() {
        return "AuthTokens[accessToken=***, refreshToken=***]";
    }
}
