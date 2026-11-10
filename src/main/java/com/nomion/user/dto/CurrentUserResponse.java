package com.nomion.user.dto;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UserResponse user,
        UUID activeTenantId,
        Set<String> roles,
        Set<String> permissions
) {
}
