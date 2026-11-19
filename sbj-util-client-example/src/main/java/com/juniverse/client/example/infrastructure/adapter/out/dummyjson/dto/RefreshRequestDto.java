package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body to renew the tokens.
 *
 * @param refreshToken refresh token
 * @param expiresInMins minutes of life of the new access token, or null for the default value
 * @author Java Universe
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefreshRequestDto(String refreshToken, Integer expiresInMins) {

    /** The token must never appear in logs. */
    @Override
    public String toString() {
        return "RefreshRequestDto[refreshToken=***]";
    }
}
