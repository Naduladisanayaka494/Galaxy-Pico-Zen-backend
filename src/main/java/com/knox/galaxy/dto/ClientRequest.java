package com.knox.galaxy.dto;

import com.knox.galaxy.model.KnoxPlan;
import com.knox.galaxy.model.KnoxSetupOption;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class ClientRequest {

    @NotBlank(message = "Business name is required")
    private String businessName;

    private String contactPerson;

    @NotBlank(message = "Phone is required")
    private String phone;

    // Required as of the auto-provisioning flow: create() emails this address
    // the new tenant's login credentials, so there's nowhere to send them
    // without it.
    @NotBlank(message = "Email is required to provision the client's Galaxy account")
    @Email(message = "Email must be valid")
    private String email;

    @NotNull(message = "Plan is required")
    private KnoxPlan plan;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Setup option is required")
    private KnoxSetupOption setupOption;

    private boolean onTrial;

    private String notes;
}
