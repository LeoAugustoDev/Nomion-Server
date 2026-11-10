package com.nomion.setup.controller;

import com.nomion.setup.dto.InitialSetupResponse;
import com.nomion.setup.dto.KeycloakInitialSetupRequest;
import com.nomion.setup.service.InitialSetupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("keycloak")
public class KeycloakInitialSetupController {

    private final InitialSetupService service;
    private final String expectedSubject;

    public KeycloakInitialSetupController(
            InitialSetupService service,
            @Value("${INITIAL_SETUP_KEYCLOAK_SUBJECT:}") String expectedSubject
    ) {
        this.service = service;
        this.expectedSubject = expectedSubject;
    }

    @PostMapping("/setup/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public InitialSetupResponse initialize(
            @AuthenticationPrincipal Jwt token,
            @RequestHeader(value = "X-Setup-Token", required = false) String setupToken,
            @RequestBody @Valid KeycloakInitialSetupRequest request
    ) {
        return service.initializeKeycloak(setupToken, request, token, expectedSubject);
    }
}
