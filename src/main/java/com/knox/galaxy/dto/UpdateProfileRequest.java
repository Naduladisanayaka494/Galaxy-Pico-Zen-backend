package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Deliberately excludes email: users.email is a local profile copy, not the
 * login identity (that's knox.tenant_users.email, in the platform directory).
 * Letting this endpoint change it would desync what the user logs in with
 * from what their profile shows, with no obvious reason why login stopped
 * working. Changing login email is a separate, not-yet-built feature.
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String phone;

    private String avatarUrl;
}

