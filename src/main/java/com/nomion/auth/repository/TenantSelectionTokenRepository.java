package com.nomion.auth.repository;

import com.nomion.auth.domain.TenantSelectionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface TenantSelectionTokenRepository extends JpaRepository<TenantSelectionToken, UUID> {


    @EntityGraph(attributePaths = "user")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TenantSelectionToken> findByIdAndConsumedAtIsNull(UUID id);

}
