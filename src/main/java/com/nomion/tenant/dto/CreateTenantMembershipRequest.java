package com.nomion.tenant.dto;

import com.nomion.user.domain.RoleCode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateTenantMembershipRequest(

        @NotNull(message = "Usuário é obrigatório")
        UUID userId,


        @NotEmpty(message = "Ao menos um perfil é obrigatório")
        Set<RoleCode> roles

) {
}
