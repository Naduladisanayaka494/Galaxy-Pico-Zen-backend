package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private Long cityId;
    private String cityName;
    private String address;

    /** Orders placed by this customer — shown on the customer picker. */
    private long orderCount;

    private LocalDateTime createdAt;
}
