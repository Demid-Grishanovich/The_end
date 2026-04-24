package com.datacrowd.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RoleChangeRequest {

    @NotBlank(message = "role must not be blank")
    @Pattern(
            regexp = "^(WORKER|CLIENT|ADMIN|REVIEWER)$",
            message = "role must be one of: WORKER, CLIENT, ADMIN, REVIEWER"
    )
    public String role;
}