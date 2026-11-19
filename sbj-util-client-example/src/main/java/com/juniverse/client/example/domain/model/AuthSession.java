package com.juniverse.client.example.domain.model;

/**
 * Result of logging in: the user and their tokens.
 *
 * @param user the authenticated user
 * @param tokens the session tokens
 * @author Java Universe
 */
public record AuthSession(UserProfile user, AuthTokens tokens) {
}
