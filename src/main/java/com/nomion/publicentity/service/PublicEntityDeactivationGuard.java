package com.nomion.publicentity.service;

import java.util.UUID;

public interface PublicEntityDeactivationGuard {

    void validate(UUID tenantId, UUID publicEntityId);
}
