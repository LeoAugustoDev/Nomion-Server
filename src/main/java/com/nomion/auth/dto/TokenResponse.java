package com.nomion.auth.dto;

import com.nomion.tenant.dto.TenantMembershipResponse;

public record TokenResponse(

        String accessToken,

        String refreshToken,

        String tokenType,

        long expiresIn,

        TenantMembershipResponse tenant

) {
}
