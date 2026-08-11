package com.nomion.contract.domain;

import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contracts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "public_entity_id", nullable = false)
    private PublicEntity publicEntity;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_membership_id")
    private TenantMembership managerMembership;


    @Column(nullable = false, length = 50)
    private String number;


    @Column(nullable = false, length = 2000)
    private String object;


    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;


    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;


    @Column(precision = 19, scale = 2)
    private BigDecimal amount;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;


    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;


    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    @Version
    @Column(nullable = false)
    private long version;


    public void update(
            PublicEntity publicEntity,
            TenantMembership managerMembership,
            String number,
            String object,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            ContractStatus status,
            UUID updatedByUserId
    ) {

        this.publicEntity = publicEntity;
        this.managerMembership = managerMembership;
        this.number = number;
        this.object = object;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.status = status;
        this.updatedByUserId = updatedByUserId;
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
