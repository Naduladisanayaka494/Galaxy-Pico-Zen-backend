package com.knox.galaxy.dto;

import lombok.Data;

@Data
public class ProductImageDto {
    private Long id;
    private String url;
    private Short position;
    private boolean isDefault;
}
