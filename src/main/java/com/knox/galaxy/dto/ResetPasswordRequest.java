package com.knox.galaxy.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New password is required")
    private String newPassword;
}
