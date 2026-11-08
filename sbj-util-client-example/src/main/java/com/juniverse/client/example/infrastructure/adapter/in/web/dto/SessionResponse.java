package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.AuthSession;

/**
 * Sesión iniciada en la API REST del ejemplo.
 *
 * @param user el usuario
 * @param tokens los tokens de la sesión
 */
public record SessionResponse(UserResponse user, TokensResponse tokens) {

    /**
     * Convierte una sesión del dominio.
     *
     * @param session la sesión
     * @return la respuesta
     */
    public static SessionResponse from(AuthSession session) {
        return new SessionResponse(UserResponse.from(session.user()), TokensResponse.from(session.tokens()));
    }
}
