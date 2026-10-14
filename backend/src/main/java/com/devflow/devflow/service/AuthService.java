package com.devflow.devflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.dto.RegistrationRequestDTO;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.util.EmailAddress;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // REGISTER
    public User registerUser(RegistrationRequestDTO request) {

        // Check if email already exists
        if (request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }
        String email = EmailAddress.normalize(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        // Hash the password before saving
        User user = new User();
        user.setName(request.getName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Every newly registered user starts as VIEWER.
        user.setRole("VIEWER");

        // Save user to MySQL
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    // LOGIN
    public String loginUser(String email, String password) {

        // Find user by email
        User user = userRepository.findByEmailIgnoreCase(EmailAddress.normalize(email))
                .filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        // Check entered password against stored BCrypt password
        if (!passwordEncoder.matches(
                password,
                user.getPassword())) {

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Generate JWT
        return jwtService.generateToken(
                user.getEmail(),
                user.getRole()
        );
    }
} 
