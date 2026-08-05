package com.nomion.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectTenantRequest(

        @NotBlank(message = "Token de seleção é obrigatório")
        String tenantSelectionToken,


        @NotNull(message = "Tenant é obrigatório")
        UUID tenantId

) {
}
