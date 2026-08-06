package com.nomion.publicentity.repository;

import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.domain.PublicEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PublicEntityRepository extends JpaRepository<PublicEntity, UUID> {


    Optional<PublicEntity> findByIdAndTenant_Id(UUID id, UUID tenantId);


    boolean existsByTenant_IdAndCnpj(UUID tenantId, String cnpj);


    boolean existsByTenant_IdAndCnpjAndIdNot(
            UUID tenantId,
            String cnpj,
            UUID id
    );


    @Query("""
            SELECT entity
            FROM PublicEntity entity
            WHERE entity.tenant.id = :tenantId
              AND (
                  :name = ''
                  OR LOWER(entity.name) LIKE CONCAT(:name, '%') ESCAPE '\\'
              )
              AND (:cnpj = '' OR entity.cnpj = :cnpj)
              AND (:type IS NULL OR entity.type = :type)
              AND (
                  :municipality = ''
                  OR LOWER(entity.municipality) LIKE CONCAT(:municipality, '%') ESCAPE '\\'
              )
              AND (:state = '' OR entity.state = :state)
              AND (:active IS NULL OR entity.active = :active)
            """)
    Page<PublicEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("cnpj") String cnpj,
            @Param("type") PublicEntityType type,
            @Param("municipality") String municipality,
            @Param("state") String state,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
