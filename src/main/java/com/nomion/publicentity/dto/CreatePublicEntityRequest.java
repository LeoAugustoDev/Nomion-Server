package com.nomion.publicentity.dto;

import com.nomion.publicentity.domain.PublicEntityType;
import com.nomion.validation.Cnpj;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePublicEntityRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
        String name,


        @Cnpj
        @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
        String cnpj,


        @NotNull(message = "Tipo é obrigatório")
        PublicEntityType type,


        @NotBlank(message = "Município é obrigatório")
        @Size(
                min = 2,
                max = 120,
                message = "Município deve ter entre 2 e 120 caracteres"
        )
        String municipality,


        @NotBlank(message = "UF é obrigatória")
        @Pattern(regexp = "(?i)^[a-z]{2}$", message = "UF deve possuir duas letras")
        String state,


        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
        String email,


        @Size(max = 30, message = "Telefone deve ter no máximo 30 caracteres")
        String phone

) {

    public CreatePublicEntityRequest {
        name = normalizeRequired(name);
        cnpj = normalizeOptional(cnpj);
        municipality = normalizeRequired(municipality);
        state = normalizeRequired(state);
        email = normalizeOptional(email);
        phone = normalizeOptional(phone);
    }


    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


    private static String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }
}
