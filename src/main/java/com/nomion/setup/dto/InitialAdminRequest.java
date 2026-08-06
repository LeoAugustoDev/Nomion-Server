package com.nomion.setup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitialAdminRequest(

        @NotBlank(message = "Nome do administrador é obrigatório")
        @Size(
                min = 3,
                max = 100,
                message = "Nome do administrador deve ter entre 3 e 100 caracteres"
        )
        String name,


        @NotBlank(message = "E-mail do administrador é obrigatório")
        @Email(message = "E-mail do administrador inválido")
        @Size(
                max = 150,
                message = "E-mail do administrador deve ter no máximo 150 caracteres"
        )
        String email,


        @NotBlank(message = "Senha do administrador é obrigatória")
        @Size(
                min = 8,
                max = 72,
                message = "Senha do administrador deve ter entre 8 e 72 caracteres"
        )
        String password

) {
}
