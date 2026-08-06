package com.nomion.publicentity.mapper;

import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.dto.CreatePublicEntityRequest;
import com.nomion.publicentity.dto.PublicEntityResponse;
import com.nomion.publicentity.dto.UpdatePublicEntityRequest;
import com.nomion.tenant.domain.Tenant;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PublicEntityMapper {


    public PublicEntity toEntity(
            CreatePublicEntityRequest request,
            Tenant tenant,
            String normalizedCnpj
    ) {

        return PublicEntity.builder()
                .tenant(tenant)
                .name(request.name().trim())
                .cnpj(normalizedCnpj)
                .type(request.type())
                .municipality(request.municipality().trim())
                .state(request.state().trim().toUpperCase(Locale.ROOT))
                .email(normalizeEmail(request.email()))
                .phone(normalizeOptional(request.phone()))
                .active(true)
                .build();
    }


    public void update(
            PublicEntity entity,
            UpdatePublicEntityRequest request,
            String normalizedCnpj
    ) {

        entity.update(
                request.name().trim(),
                normalizedCnpj,
                request.type(),
                request.municipality().trim(),
                request.state().trim().toUpperCase(Locale.ROOT),
                normalizeEmail(request.email()),
                normalizeOptional(request.phone()),
                request.active()
        );
    }


    public PublicEntityResponse toResponse(PublicEntity entity) {

        return new PublicEntityResponse(
                entity.getId(),
                entity.getName(),
                entity.getCnpj(),
                entity.getType(),
                entity.getMunicipality(),
                entity.getState(),
                entity.getEmail(),
                entity.getPhone(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }


    private String normalizeEmail(String email) {

        String value = normalizeOptional(email);

        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }


    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
