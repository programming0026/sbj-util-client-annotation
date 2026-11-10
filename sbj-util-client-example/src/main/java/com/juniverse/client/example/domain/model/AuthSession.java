package com.juniverse.client.example.domain.model;

/**
 * Resultado de iniciar sesión: el usuario y sus tokens.
 *
 * @param user el usuario autenticado
 * @param tokens los tokens de la sesión
 * @author Java Universe
 */
public record AuthSession(UserProfile user, AuthTokens tokens) {
}
