package com.nomion.auth.dto;

import com.nomion.tenant.dto.TenantMembershipResponse;

import java.util.Set;

public record TokenResponse(

        String accessToken,

        String refreshToken,

        String tokenType,

        long expiresIn,

        TenantMembershipResponse tenant,

        Set<String> roles,

        Set<String> permissions

) {
}
