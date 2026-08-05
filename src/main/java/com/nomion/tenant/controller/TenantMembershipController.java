package com.nomion.tenant.controller;

import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.dto.CreateTenantMembershipRequest;
import com.nomion.tenant.dto.TenantMembershipResponse;
import com.nomion.tenant.service.TenantMembershipManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/tenants/{tenantId}/memberships")
public class TenantMembershipController {


    private final TenantMembershipManagementService service;


    public TenantMembershipController(TenantMembershipManagementService service) {
        this.service = service;
    }


    @PostMapping
    @PreAuthorize("hasAuthority('MEMBERSHIP_CREATE') and hasAuthority('ROLE_ASSIGN')")
    public ResponseEntity<TenantMembershipResponse> create(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid CreateTenantMembershipRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(tenantId, request, authenticatedUser)
        );
    }
}
