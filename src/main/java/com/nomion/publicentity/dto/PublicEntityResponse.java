package com.nomion.publicentity.dto;

import com.nomion.publicentity.domain.PublicEntityType;

import java.time.Instant;
import java.util.UUID;

public record PublicEntityResponse(

        UUID id,

        String name,

        String cnpj,

        PublicEntityType type,

        String municipality,

        String state,

        String email,

        String phone,

        boolean active,

        Instant createdAt,

        Instant updatedAt

) {
}
