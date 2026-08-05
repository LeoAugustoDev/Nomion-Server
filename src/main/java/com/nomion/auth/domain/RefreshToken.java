package com.nomion.auth.domain;

import com.nomion.tenant.domain.TenantMembership;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private TenantMembership membership;


    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;


    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @Column(name = "revoked_at")
    private Instant revokedAt;


    @Version
    @Column(nullable = false)
    private long version;


    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
