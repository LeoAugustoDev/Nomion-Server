package com.nomion.security;

import java.util.UUID;
import java.util.Set;

public record AuthenticatedUser(

        UUID id,

        UUID membershipId,

        UUID tenantId,

        String email,

        Set<String> roles,

        Set<String> permissions

) {
}
