package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

/**
 * User name and password to log in.
 *
 * @param username the user name
 * @param password the password
 * @author Java Universe
 */
public record Credentials(String username, String password) {

    /**
     * Validates that both values are present.
     *
     * @throws InvalidRequestException if the user name or the password is missing
     */
    public Credentials {
        if (username == null || username.isBlank()) {
            throw new InvalidRequestException("username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new InvalidRequestException("password is required");
        }
        username = username.trim();
    }

    /** The password must never appear in logs or error messages. */
    @Override
    public String toString() {
        return "Credentials[username=" + username + ", password=***]";
    }
}
