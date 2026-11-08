package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.AuthTokens;

/**
 * Tokens en la API REST del ejemplo.
 *
 * @param accessToken token de acceso
 * @param refreshToken token de renovación
 */
public record TokensResponse(String accessToken, String refreshToken) {

    /**
     * Convierte los tokens del dominio.
     *
     * @param tokens los tokens
     * @return la respuesta
     */
    public static TokensResponse from(AuthTokens tokens) {
        return new TokensResponse(tokens.accessToken(), tokens.refreshToken());
    }

    /** Los tokens nunca deben aparecer en logs. */
    @Override
    public String toString() {
        return "TokensResponse[tokens=***]";
    }
}
