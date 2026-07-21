package com.knox.galaxy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionId implements Serializable {
    // Matches Role's PK type (Short), not the Role entity itself — JPA's
    // @IdClass contract for a @ManyToOne that's part of a composite key.
    private Short role;
    private String feature;
}
