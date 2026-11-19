package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body to renew the tokens in the example's REST API.
 *
 * @param refreshToken refresh token
 * @author Java Universe
 */
public record RefreshRequest(
        @Schema(description = "Refresh token returned by the login", example = "eyJhbGciOiJIUzI1NiJ9...") String refreshToken) {

    /** The token must never appear in logs. */
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
