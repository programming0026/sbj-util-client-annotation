package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * New tokens after renewing the session.
 *
 * @param accessToken access token
 * @param refreshToken refresh token
 * @author Java Universe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshResponseDto(String accessToken, String refreshToken) {

    /** Tokens must never appear in logs. */
    @Override
    public String toString() {
        return "RefreshResponseDto[tokens=***]";
    }
}
