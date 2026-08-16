package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Create / rename a product category. Names are unique per tenant. */
@Data
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 255)
    private String name;
}
