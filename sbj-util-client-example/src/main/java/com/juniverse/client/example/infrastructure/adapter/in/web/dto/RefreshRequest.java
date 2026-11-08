package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo para renovar los tokens en la API REST del ejemplo.
 *
 * @param refreshToken token de renovación
 */
public record RefreshRequest(
        @Schema(description = "Token de renovación que devuelve el inicio de sesión", example = "eyJhbGciOiJIUzI1NiJ9...") String refreshToken) {

    /** El token nunca debe aparecer en logs. */
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
