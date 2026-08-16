package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knox.galaxy.model.CommissionMethod;
import lombok.Data;

import javax.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Create / update a tenant member from the Users screen.
 *
 * <p>{@code password} is required on create and optional on update — sending it
 * on update re-issues the person's login credential. Email is mandatory
 * because it <em>is</em> the login identifier: it lands in
 * {@code knox.tenant_users}, which is what the login flow resolves against.
 */
@Data
public class TenantUserRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 150)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 150)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Username is required")
    @Size(max = 150)
    private String username;

    /** Required on create; leave null on update to keep the current password. */
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotNull(message = "Role is required")
    private Short roleId;

    @Size(max = 50)
    private String phone;

    private String avatarUrl;

    @JsonProperty("isActive")
    private boolean isActive = true;

    // ---- Commission (§11.2). The DB enforces the shape; the service checks
    // it first so a bad combination reads as a 400, not a constraint 500.
    private boolean commissionEnabled = false;

    private CommissionMethod commissionMethod;

    @DecimalMin(value = "0.000", message = "Commission percent must be >= 0")
    private BigDecimal commissionPercent;

    @DecimalMin(value = "0.00", message = "Commission unit amount must be >= 0")
    private BigDecimal commissionUnitAmount;

    @Min(value = 0, message = "Minimum units must be >= 0")
    private Integer commissionMinUnits;
}
