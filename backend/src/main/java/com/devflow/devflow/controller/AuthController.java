package com.devflow.devflow.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.AuthResponseDTO;
import com.devflow.devflow.dto.RegistrationRequestDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registerUser(@RequestBody RegistrationRequestDTO user) {

        User registeredUser = authService.registerUser(user);

        AuthResponseDTO response = new AuthResponseDTO(
                registeredUser.getId(),
                registeredUser.getName(),
                registeredUser.getEmail(),
                registeredUser.getRole()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
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
