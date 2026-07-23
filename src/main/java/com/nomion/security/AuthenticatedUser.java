package com.nomion.security;

import java.util.UUID;

public record AuthenticatedUser(

        UUID id,

        UUID tenantId,

        String email

) {
}
