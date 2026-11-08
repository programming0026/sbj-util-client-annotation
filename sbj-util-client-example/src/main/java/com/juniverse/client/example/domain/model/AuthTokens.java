package com.juniverse.client.example.domain.model;

/**
 * Par de tokens de una sesión.
 *
 * @param accessToken token de acceso, de vida corta
 * @param refreshToken token para obtener un nuevo token de acceso
 */
public record AuthTokens(String accessToken, String refreshToken) {

    /** Los tokens nunca deben aparecer en logs. */
    @Override
    public String toString() {
        return "AuthTokens[accessToken=***, refreshToken=***]";
    }
}
