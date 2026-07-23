package com.nomion.auth.repository;

import com.nomion.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {


    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

}
