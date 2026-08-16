package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Create / edit a customer. Phone is the identity (§10.3) and must be unique. */
@Data
public class CustomerRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Phone number is required")
    @Size(max = 30)
    private String phone;

    @Email(message = "Enter a valid email address")
    @Size(max = 255)
    private String email;

    private Long cityId;

    @Size(max = 500)
    private String address;
}
