package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.UserProfile;

/**
 * User in the example's REST API.
 *
 * @param id identifier
 * @param username user name
 * @param email email
 * @param firstName first name
 * @param lastName last name
 * @param gender gender
 * @param image profile image
 * @author Java Universe
 */
public record UserResponse(long id, String username, String email, String firstName, String lastName,
                           String gender, String image) {

    /**
     * Converts a domain user.
     *
     * @param user the user
     * @return the response
     */
    public static UserResponse from(UserProfile user) {
        return new UserResponse(user.id(), user.username(), user.email(), user.firstName(), user.lastName(),
                user.gender(), user.image());
    }
}
