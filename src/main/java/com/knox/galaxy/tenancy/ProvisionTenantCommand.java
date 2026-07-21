package com.knox.galaxy.tenancy;

import com.knox.galaxy.model.BillingPlan;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class ProvisionTenantCommand {

    /** Becomes the schema name as tenant_<slug>, so the format is enforced, not advisory. */
    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]{2,30}$",
             message = "Slug must be 3-31 chars: lowercase letters, digits, underscore; starting with a letter")
    private String slug;

    @NotBlank(message = "Business name is required")
    private String businessName;

    /** Optional link to the KNOX CRM record. */
    private Long clientId;

    private BillingPlan plan;

    @NotBlank(message = "Owner email is required")
    @Email(message = "Owner email must be valid")
    private String ownerEmail;

    @NotBlank(message = "Owner password is required")
    @Size(min = 6, max = 100, message = "Owner password must be at least 6 characters")
    private String ownerPassword;

    @NotBlank(message = "Owner username is required")
    @Size(min = 3, max = 50)
    private String ownerUsername;

    @NotBlank(message = "Owner first name is required")
    private String ownerFirstName;

    @NotBlank(message = "Owner last name is required")
    private String ownerLastName;
}
