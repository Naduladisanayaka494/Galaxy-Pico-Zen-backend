package com.knox.galaxy.dto;

import com.knox.galaxy.model.CommissionMethod;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserResponseDto {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String phone;
    private String avatarUrl;
    private boolean commissionEnabled;
    private CommissionMethod commissionMethod;
    private BigDecimal commissionPercent;
    private BigDecimal commissionUnitAmount;
    private Integer commissionMinUnits;
}
