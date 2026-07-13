package com.nomion.tenant.dto;

import com.nomion.tenant.domain.Tenant;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(

        UUID id,

        String name,

        String slug,

        LocalDateTime createdAt

) {

    public static TenantResponse fromEntity(Tenant tenant) {

        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getCreatedAt()
        );

    }

}