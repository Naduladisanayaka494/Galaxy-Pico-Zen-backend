package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Body of POST /api/auth/reset-password — the emailed token plus the chosen password. */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    private String token;

    // Same floor as RegisterRequest so a reset can't set a weaker password
    // than sign-up allows.
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String newPassword;
}
