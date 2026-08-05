package com.nomion.tenant.controller;

import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.dto.CreateTenantRequest;
import com.nomion.tenant.dto.TenantResponse;
import com.nomion.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tenants")
public class TenantController {


    private final TenantService service;


    public TenantController(TenantService service) {
        this.service = service;
    }


    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @RequestBody @Valid CreateTenantRequest request
    ){

        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }


    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> findById(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id
    ){

        return ResponseEntity.ok(service.findById(id, authenticatedUser));
    }

}
