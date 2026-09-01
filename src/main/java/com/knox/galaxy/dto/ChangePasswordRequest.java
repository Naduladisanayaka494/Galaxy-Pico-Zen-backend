package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Body of PUT /api/users/me/password — the signed-in person changing their own
 * password from the Profile screen. The current password is re-checked here
 * even though the caller is already authenticated: it stops someone acting on
 * an unlocked, unattended session from locking the real owner out.
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String newPassword;
}
