package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knox.galaxy.model.CommissionMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A tenant member as shown on the Users screen. Never carries a password or
 * hash — the credential lives in {@code knox.tenant_users} and is write-only
 * through {@link TenantUserRequest}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantUserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String username;
    private Short roleId;
    private String role;
    private String phone;
    private String avatarUrl;

    /** See {@link CityResponse#isActive} for why the name is pinned. */
    @JsonProperty("isActive")
    private boolean isActive;

    private LocalDateTime lastLoginAt;
    private boolean commissionEnabled;
    private CommissionMethod commissionMethod;
    private BigDecimal commissionPercent;
    private BigDecimal commissionUnitAmount;
    private Integer commissionMinUnits;
}
