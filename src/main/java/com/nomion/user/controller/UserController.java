package com.nomion.user.controller;

import com.nomion.security.AuthenticatedUser;
import com.nomion.user.dto.CreateUserRequest;
import com.nomion.user.dto.UserResponse;
import com.nomion.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {


    private final UserService service;


    public UserController(UserService service) {
        this.service = service;
    }


    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE') and hasAuthority('ROLE_ASSIGN')")
    public ResponseEntity<UserResponse> create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid CreateUserRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(request, authenticatedUser)
        );
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<UserResponse> findById(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id
    ) {

        return ResponseEntity.ok(service.findById(id, authenticatedUser));
    }
}
