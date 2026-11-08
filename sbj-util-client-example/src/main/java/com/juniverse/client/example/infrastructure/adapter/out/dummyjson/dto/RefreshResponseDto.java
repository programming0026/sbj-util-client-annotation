package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tokens nuevos tras renovar la sesión.
 *
 * @param accessToken token de acceso
 * @param refreshToken token de renovación
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshResponseDto(String accessToken, String refreshToken) {

    /** Los tokens nunca deben aparecer en logs. */
    @Override
    public String toString() {
        return "RefreshResponseDto[tokens=***]";
    }
}
