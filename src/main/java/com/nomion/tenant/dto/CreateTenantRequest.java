package com.nomion.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        String name,


        @NotBlank(message = "Slug é obrigatório")
        @Pattern(
                regexp = "^[a-z0-9-]+$",
                message = "Slug deve conter apenas letras minúsculas, números e hífen"
        )
        @Size(min = 3, max = 50, message = "Slug deve ter entre 3 e 50 caracteres")
        String slug

) {
}