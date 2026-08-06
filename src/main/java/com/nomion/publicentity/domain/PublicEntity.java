package com.nomion.publicentity.domain;

import com.nomion.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "public_entities")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;


    @Column(nullable = false, length = 150)
    private String name;


    @Column(length = 14)
    private String cnpj;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PublicEntityType type;


    @Column(nullable = false, length = 120)
    private String municipality;


    @Column(nullable = false, length = 2)
    private String state;


    @Column(length = 150)
    private String email;


    @Column(length = 30)
    private String phone;


    @Column(nullable = false)
    private boolean active;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    @Version
    @Column(nullable = false)
    private long version;


    public void update(
            String name,
            String cnpj,
            PublicEntityType type,
            String municipality,
            String state,
            String email,
            String phone,
            boolean active
    ) {

        this.name = name;
        this.cnpj = cnpj;
        this.type = type;
        this.municipality = municipality;
        this.state = state;
        this.email = email;
        this.phone = phone;
        this.active = active;
    }


    @PrePersist
    public void prePersist() {

        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
    }


    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
