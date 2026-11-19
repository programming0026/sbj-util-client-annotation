package com.juniverse.client.example.domain.model;

/**
 * Public data of a user.
 *
 * @param id identifier
 * @param username user name
 * @param email email
 * @param firstName first name
 * @param lastName last name
 * @param gender gender, or null
 * @param image profile image URL, or null
 * @author Java Universe
 */
public record UserProfile(long id, String username, String email, String firstName, String lastName, String gender,
                          String image) {
}
