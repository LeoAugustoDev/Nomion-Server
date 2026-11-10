package com.nomion.user.controller;

import com.nomion.security.AuthenticatedUser;
import com.nomion.user.dto.InviteUserRequest;
import com.nomion.user.service.UserInvitationService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("keycloak")
public class UserInvitationController {

    private final UserInvitationService service;

    public UserInvitationController(UserInvitationService service) {
        this.service = service;
    }

    @PostMapping("/users/invitations")
    @PreAuthorize("hasAuthority('USER_CREATE') and hasAuthority('ROLE_ASSIGN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(
            @AuthenticationPrincipal AuthenticatedUser administrator,
            @RequestHeader("Idempotency-Key") UUID requestId,
            @RequestBody @Valid InviteUserRequest request
    ) {
        service.invite(requestId, request, administrator);
    }
}
