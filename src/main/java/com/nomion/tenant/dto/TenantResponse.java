package com.nomion.tenant.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(

        UUID id,

        String name,

        String slug,

        LocalDateTime createdAt

) {
}
