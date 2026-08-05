package com.nomion.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchTenantRequest(

        @NotNull(message = "Tenant é obrigatório")
        UUID tenantId,


        @NotBlank(message = "Refresh token é obrigatório")
        String refreshToken

) {
}
