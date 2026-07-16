package com.nomion.user.controller;

import com.nomion.user.dto.CreateUserRequest;
import com.nomion.user.dto.UserResponse;
import com.nomion.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<UserResponse> create(
            @RequestBody @Valid CreateUserRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }


    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(
            @PathVariable UUID id
    ) {

        return ResponseEntity.ok(service.findById(id));
    }
}
