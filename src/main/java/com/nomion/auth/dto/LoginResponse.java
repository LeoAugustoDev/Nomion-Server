package com.nomion.auth.dto;

import com.nomion.tenant.dto.TenantMembershipResponse;
import com.nomion.user.dto.UserInfoResponse;

import java.util.List;

public record LoginResponse(

        LoginStatus status,

        UserInfoResponse user,

        TokenResponse tokens,

        String tenantSelectionToken,

        long tenantSelectionExpiresIn,

        List<TenantMembershipResponse> tenants

) {
}
