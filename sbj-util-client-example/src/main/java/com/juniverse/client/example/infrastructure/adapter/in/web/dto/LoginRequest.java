package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.Credentials;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body to log in to the example's REST API.
 *
 * @param username user name
 * @param password password
 * @author Java Universe
 */
public record LoginRequest(
        @Schema(description = "User name", example = "emilys") String username,
        @Schema(description = "Password", example = "emilyspass", format = "password") String password) {

    /**
     * Converts the body into domain credentials.
     *
     * @return the credentials
     */
    public Credentials toCredentials() {
        return new Credentials(username, password);
    }

    /** The password must never appear in logs. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
