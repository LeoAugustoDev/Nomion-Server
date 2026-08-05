package com.nomion.security;

import java.util.UUID;

public record TenantSelectionPrincipal(

        UUID tokenId,

        UUID userId

) {
}
