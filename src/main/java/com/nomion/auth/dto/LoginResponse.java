package com.nomion.auth.dto;

import com.nomion.tenant.dto.TenantMembershipResponse;

import java.util.List;

public record LoginResponse(

        LoginStatus status,

        TokenResponse tokens,

        String tenantSelectionToken,

        long tenantSelectionExpiresIn,

        List<TenantMembershipResponse> tenants

) {
}
