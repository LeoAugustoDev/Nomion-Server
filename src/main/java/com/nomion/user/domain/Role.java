package com.nomion.user.domain;

import com.nomion.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {


    @Id
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;


    @Column(nullable = false, length = 100)
    private String code;


    @Column(nullable = false, length = 120)
    private String name;


    @Column(length = 255)
    private String description;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleScope scope;


    @Column(name = "system_role", nullable = false)
    private boolean systemRole;


    @Column(nullable = false)
    private boolean active;


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

        this.createdAt = now;
        this.updatedAt = now;
        this.active = true;
    }


    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
