package com.nomion.setup.dto;

import com.nomion.tenant.dto.CreateTenantRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KeycloakInitialSetupRequest(
        @NotNull @Valid CreateTenantRequest tenant,
        @NotBlank @Size(min = 3, max = 100) String administratorName
) {
}
