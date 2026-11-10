package com.nomion.setup.controller;

import com.nomion.setup.dto.InitialSetupRequest;
import com.nomion.setup.dto.InitialSetupResponse;
import com.nomion.setup.service.InitialSetupService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.context.annotation.Profile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!keycloak")
@RequestMapping("/setup")
public class InitialSetupController {


    private final InitialSetupService service;


    public InitialSetupController(InitialSetupService service) {
        this.service = service;
    }


    @PostMapping("/initialize")
    @SecurityRequirements
    public ResponseEntity<InitialSetupResponse> initialize(
            @RequestHeader(value = "X-Setup-Token", required = false)
            String setupToken,
            @RequestBody @Valid InitialSetupRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.initialize(setupToken, request)
        );
    }
}
