package com.nomion.auth.domain;

import com.nomion.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_selection_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSelectionToken {


    @Id
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @Column(name = "consumed_at")
    private Instant consumedAt;


    @Version
    @Column(nullable = false)
    private long version;


    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
