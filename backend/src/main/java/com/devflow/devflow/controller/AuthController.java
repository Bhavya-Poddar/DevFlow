package com.devflow.devflow.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Administrator-provisioned accounts and JWT login.")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Public registration disabled", description = "Accounts are provisioned through the protected administrator user-management flow.")
    @ApiResponse(responseCode = "403", description = "Public account registration is disabled.")
    public ResponseEntity<Void> registerUser(@RequestBody java.util.Map<String, Object> ignoredRequest) {
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "Public registration is disabled. Contact a workspace administrator.");
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates with email and password and returns a JWT access token.")
    @ApiResponse(responseCode = "200", description = "Credentials accepted; returns a JWT bearer token.")
    public ResponseEntity<Map<String, String>> loginUser(
            @RequestBody User user) {

        String token = authService.loginUser(
                user.getEmail(),
                user.getPassword()
        );

        return ResponseEntity.ok(
                Map.of("token", token)
        );
    }
}
