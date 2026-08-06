package com.nomion.auth.controller;

import com.nomion.auth.dto.LoginRequest;
import com.nomion.auth.dto.LoginResponse;
import com.nomion.auth.dto.RefreshTokenRequest;
import com.nomion.auth.dto.SelectTenantRequest;
import com.nomion.auth.dto.SwitchTenantRequest;
import com.nomion.auth.dto.TokenResponse;
import com.nomion.auth.service.AuthService;
import com.nomion.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {


    private final AuthService service;


    public AuthController(AuthService service) {
        this.service = service;
    }


    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<LoginResponse> login(
            @RequestBody @Valid LoginRequest request
    ) {

        return ResponseEntity.ok(service.login(request));
    }


    @PostMapping("/select-tenant")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> selectTenant(
            @RequestBody @Valid SelectTenantRequest request
    ) {

        return ResponseEntity.ok(service.selectTenant(request));
    }


    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody @Valid RefreshTokenRequest request
    ) {

        return ResponseEntity.ok(service.refresh(request));
    }


    @PostMapping("/switch-tenant")
    public ResponseEntity<TokenResponse> switchTenant(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid SwitchTenantRequest request
    ) {

        return ResponseEntity.ok(service.switchTenant(authenticatedUser, request));
    }


    @PostMapping("/logout")
    @SecurityRequirements
    public ResponseEntity<Void> logout(
            @RequestBody @Valid RefreshTokenRequest request
    ) {

        service.logout(request);

        return ResponseEntity.noContent().build();
    }
}
