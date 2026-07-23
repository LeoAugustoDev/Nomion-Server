package com.nomion.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,


        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
        String password

) {
}
