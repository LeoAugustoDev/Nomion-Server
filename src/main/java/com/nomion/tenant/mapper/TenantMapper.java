package com.nomion.tenant.mapper;

import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.dto.CreateTenantRequest;
import com.nomion.tenant.dto.TenantResponse;
import org.springframework.stereotype.Component;

@Component
public class TenantMapper {

    public Tenant toEntity(CreateTenantRequest request) {

        return Tenant.builder()
                .name(request.name())
                .slug(request.slug())
                .build();
    }

    public TenantResponse toResponse(Tenant tenant) {

        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getCreatedAt()
        );
    }
}
