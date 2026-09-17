package com.knox.galaxy.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    private String newPassword;
}
