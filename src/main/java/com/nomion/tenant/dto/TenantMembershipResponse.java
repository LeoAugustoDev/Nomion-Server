package com.nomion.tenant.dto;

import com.nomion.tenant.domain.TenantMembershipStatus;

import java.util.UUID;

public record TenantMembershipResponse(

        UUID membershipId,

        UUID tenantId,

        String tenantName,

        String tenantSlug,

        TenantMembershipStatus status

) {
}
