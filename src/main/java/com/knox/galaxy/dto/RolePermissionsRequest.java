package com.knox.galaxy.dto;

import com.knox.galaxy.model.AccessLevel;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Full replace of one role's permission matrix: feature key → access level.
 *
 * <p>Features absent from the map are deleted, which is what the Users screen
 * wants — it always posts the whole column it just edited.
 */
@Data
public class RolePermissionsRequest {

    @NotNull(message = "Permissions map is required")
    private Map<String, AccessLevel> permissions;
}
