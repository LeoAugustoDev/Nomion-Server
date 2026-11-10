package com.nomion.user.controller;

import com.nomion.security.AuthenticatedUser;
import com.nomion.user.dto.CurrentUserResponse;
import com.nomion.user.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("keycloak")
public class CurrentUserController {

    private final UserService userService;

    public CurrentUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.currentUser(user);
    }
}
