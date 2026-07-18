package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformLoginResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private String email;
    private String fullName;
}
