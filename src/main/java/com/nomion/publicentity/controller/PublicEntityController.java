package com.nomion.publicentity.controller;

import com.nomion.publicentity.domain.PublicEntityType;
import com.nomion.publicentity.dto.CreatePublicEntityRequest;
import com.nomion.publicentity.dto.PublicEntityFilter;
import com.nomion.publicentity.dto.PublicEntityPageResponse;
import com.nomion.publicentity.dto.PublicEntityResponse;
import com.nomion.publicentity.dto.UpdatePublicEntityRequest;
import com.nomion.publicentity.service.PublicEntityService;
import com.nomion.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/public-entities")
public class PublicEntityController {


    private final PublicEntityService service;


    public PublicEntityController(PublicEntityService service) {
        this.service = service;
    }


    @PostMapping
    @PreAuthorize("hasAuthority('PUBLIC_ENTITY_CREATE')")
    public ResponseEntity<PublicEntityResponse> create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid CreatePublicEntityRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(request, authenticatedUser)
        );
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PUBLIC_ENTITY_READ')")
    public ResponseEntity<PublicEntityResponse> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {

        return ResponseEntity.ok(service.findById(id, authenticatedUser));
    }


    @GetMapping
    @PreAuthorize("hasAuthority('PUBLIC_ENTITY_READ')")
    public ResponseEntity<PublicEntityPageResponse> findAll(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) PublicEntityType type,
            @RequestParam(required = false) String municipality,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {

        PublicEntityFilter filter = new PublicEntityFilter(
                name,
                cnpj,
                type,
                municipality,
                state,
                active
        );

        return ResponseEntity.ok(service.findAll(
                filter,
                page,
                size,
                authenticatedUser
        ));
    }


    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PUBLIC_ENTITY_UPDATE')")
    public ResponseEntity<PublicEntityResponse> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid UpdatePublicEntityRequest request
    ) {

        return ResponseEntity.ok(service.update(id, request, authenticatedUser));
    }
}
