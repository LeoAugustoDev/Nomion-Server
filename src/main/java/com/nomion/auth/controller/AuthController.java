package com.nomion.auth.controller;

import com.nomion.auth.dto.LoginRequest;
import com.nomion.auth.dto.RefreshTokenRequest;
import com.nomion.auth.dto.TokenResponse;
import com.nomion.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {


    private final AuthService service;


    public AuthController(AuthService service) {
        this.service = service;
    }


    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @RequestBody @Valid LoginRequest request
    ) {

        return ResponseEntity.ok(service.login(request));
    }


    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody @Valid RefreshTokenRequest request
    ) {

        return ResponseEntity.ok(service.refresh(request));
    }


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody @Valid RefreshTokenRequest request
    ) {

        service.logout(request);

        return ResponseEntity.noContent().build();
    }
}
