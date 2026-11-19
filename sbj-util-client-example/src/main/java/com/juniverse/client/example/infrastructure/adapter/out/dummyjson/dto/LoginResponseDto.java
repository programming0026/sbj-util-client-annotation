package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Login response: the user and their tokens.
 *
 * The numbers are wrapper types on purpose: DummyJSON omits fields in some responses (for example
 * when creating or modifying) and Jackson 3 rejects a missing primitive field.
 *
 * @param id user identifier
 * @param username user name
 * @param email email
 * @param firstName first name
 * @param lastName last name
 * @param gender gender
 * @param image profile image
 * @param accessToken access token
 * @param refreshToken refresh token
 * @author Java Universe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponseDto(Long id, String username, String email, String firstName, String lastName,
                               String gender, String image, String accessToken, String refreshToken) {

    /** Tokens must never appear in logs. */
    @Override
    public String toString() {
        return "LoginResponseDto[id=" + id + ", username=" + username + ", tokens=***]";
    }
}
