package com.nomion.setup.dto;

import com.nomion.tenant.dto.CreateTenantRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record InitialSetupRequest(

        @NotNull(message = "Tenant é obrigatório")
        @Valid
        CreateTenantRequest tenant,


        @NotNull(message = "Administrador é obrigatório")
        @Valid
        InitialAdminRequest administrator

) {
}
