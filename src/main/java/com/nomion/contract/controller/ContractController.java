package com.nomion.contract.controller;

import com.nomion.contract.domain.ContractStatus;
import com.nomion.contract.dto.ContractFilter;
import com.nomion.contract.dto.ContractPageResponse;
import com.nomion.contract.dto.ContractResponse;
import com.nomion.contract.dto.CreateContractRequest;
import com.nomion.contract.dto.UpdateContractRequest;
import com.nomion.contract.service.ContractService;
import com.nomion.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/contracts")
public class ContractController {


    private final ContractService service;


    public ContractController(ContractService service) {
        this.service = service;
    }


    @PostMapping
    @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    public ResponseEntity<ContractResponse> create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid CreateContractRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(request, authenticatedUser)
        );
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTRACT_READ')")
    public ResponseEntity<ContractResponse> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {

        return ResponseEntity.ok(service.findById(id, authenticatedUser));
    }


    @GetMapping
    @PreAuthorize("hasAuthority('CONTRACT_READ')")
    public ResponseEntity<ContractPageResponse> findAll(
            @RequestParam(required = false) String number,
            @RequestParam(required = false) UUID publicEntityId,
            @RequestParam(required = false) UUID managerMembershipId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDateTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {

        ContractFilter filter = new ContractFilter(
                number,
                publicEntityId,
                managerMembershipId,
                status,
                startDateFrom,
                endDateTo
        );

        return ResponseEntity.ok(service.findAll(
                filter,
                page,
                size,
                authenticatedUser
        ));
    }


    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTRACT_UPDATE')")
    public ResponseEntity<ContractResponse> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid UpdateContractRequest request
    ) {

        return ResponseEntity.ok(service.update(id, request, authenticatedUser));
    }
}
