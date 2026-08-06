package com.nomion.setup.dto;

import com.nomion.tenant.dto.TenantResponse;
import com.nomion.user.dto.UserResponse;

import java.time.Instant;

public record InitialSetupResponse(

        TenantResponse tenant,

        UserResponse administrator,

        Instant initializedAt

) {
}
