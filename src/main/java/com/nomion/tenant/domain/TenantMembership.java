package com.nomion.tenant.domain;

import com.nomion.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "tenant_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tenant_memberships_user_tenant",
                columnNames = {"user_id", "tenant_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMembership {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantMembershipStatus status;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    @Version
    @Column(nullable = false)
    private long version;


    @PrePersist
    public void prePersist() {

        Instant now = Instant.now();

        if (this.status == null) {
            this.status = TenantMembershipStatus.ACTIVE;
        }

        this.createdAt = now;
        this.updatedAt = now;
    }


    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
