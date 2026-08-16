package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {
    private Short id;
    private String name;

    /** True for 'owner' — the one role every tenant is guaranteed to have. */
    @JsonProperty("isSystem")
    private boolean isSystem;

    /** How many members currently hold this role. */
    private long userCount;
}
