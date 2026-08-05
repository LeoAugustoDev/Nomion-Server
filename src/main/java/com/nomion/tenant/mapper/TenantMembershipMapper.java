package com.nomion.tenant.mapper;

import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.dto.TenantMembershipResponse;
import org.springframework.stereotype.Component;

@Component
public class TenantMembershipMapper {

    public TenantMembershipResponse toResponse(TenantMembership membership) {

        return new TenantMembershipResponse(
                membership.getId(),
                membership.getTenant().getId(),
                membership.getTenant().getName(),
                membership.getTenant().getSlug(),
                membership.getStatus()
        );
    }
}
