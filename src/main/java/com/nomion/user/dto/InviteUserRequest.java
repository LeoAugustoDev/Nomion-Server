package com.nomion.user.dto;

import com.nomion.user.domain.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record InviteUserRequest(
        @NotBlank @Size(min = 3, max = 100) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @NotEmpty Set<@NotNull RoleCode> roles
) {
}
