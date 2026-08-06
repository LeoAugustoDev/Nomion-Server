package com.nomion.publicentity.mapper;

import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.domain.PublicEntityType;
import com.nomion.publicentity.dto.CreatePublicEntityRequest;
import com.nomion.publicentity.dto.UpdatePublicEntityRequest;
import com.nomion.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicEntityMapperTest {


    private final PublicEntityMapper mapper = new PublicEntityMapper();


    @Test
    void shouldNormalizeFieldsWhenCreatingEntity() {

        Tenant tenant = Tenant.builder().build();
        CreatePublicEntityRequest request = new CreatePublicEntityRequest(
                "  Prefeitura Municipal  ",
                "11.222.333/0001-81",
                PublicEntityType.CITY_HALL,
                "  Campinas  ",
                "sp",
                "  CONTATO@EXAMPLE.COM  ",
                "  (19) 99999-9999  "
        );

        PublicEntity entity = mapper.toEntity(
                request,
                tenant,
                "11222333000181"
        );

        assertEquals(tenant, entity.getTenant());
        assertEquals("Prefeitura Municipal", entity.getName());
        assertEquals("11222333000181", entity.getCnpj());
        assertEquals("Campinas", entity.getMunicipality());
        assertEquals("SP", entity.getState());
        assertEquals("contato@example.com", entity.getEmail());
        assertEquals("(19) 99999-9999", entity.getPhone());
        assertTrue(entity.isActive());
    }


    @Test
    void shouldClearOptionalFieldsAndDeactivateEntity() {

        PublicEntity entity = PublicEntity.builder()
                .active(true)
                .email("old@example.com")
                .phone("old-phone")
                .build();
        UpdatePublicEntityRequest request = new UpdatePublicEntityRequest(
                "Câmara Municipal",
                null,
                PublicEntityType.CITY_COUNCIL,
                "Campinas",
                "SP",
                null,
                null,
                false
        );

        mapper.update(entity, request, null);

        assertEquals("Câmara Municipal", entity.getName());
        assertNull(entity.getCnpj());
        assertNull(entity.getEmail());
        assertNull(entity.getPhone());
        assertFalse(entity.isActive());
    }
}
