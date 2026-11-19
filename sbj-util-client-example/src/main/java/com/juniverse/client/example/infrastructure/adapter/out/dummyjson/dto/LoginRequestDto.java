package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body to log in.
 *
 * @param username user name
 * @param password password
 * @param expiresInMins minutes of life of the access token, or null for the service default
 * @author Java Universe
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginRequestDto(String username, String password, Integer expiresInMins) {

    /** The password must never appear in logs. */
    @Override
    public String toString() {
        return "LoginRequestDto[username=" + username + ", password=***]";
    }
}
