package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/**
 * Login is by email, not username: usernames are unique only within a tenant,
 * so "isuru" cannot identify a person until the tenant is known — and the whole
 * point of the platform directory is to find the tenant from the credentials.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password cannot be blank")
    private String password;
}
