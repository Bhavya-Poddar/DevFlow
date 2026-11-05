package com.devflow.devflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.dto.RegistrationRequestDTO;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.util.EmailAddress;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditLogServices auditLogService;

    // REGISTER
    @Transactional
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
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        auditLogService.createLog("USER_REGISTERED", "targetType=USER; targetId=" + savedUser.getId()
                + "; public account registered with VIEWER role", null, savedUser.getId());
        return savedUser;
    }

    // LOGIN
    public String loginUser(String email, String password) {

        // Find user by email
        User user = userRepository.findByEmailIgnoreCase(EmailAddress.normalize(email)).orElse(null);
        if (user == null || !user.isEnabled()) {
            recordFailedLogin(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Check entered password against stored BCrypt password
        if (!passwordEncoder.matches(
                password,
                user.getPassword())) {
            recordFailedLogin(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Generate JWT
        String token = jwtService.generateToken(
                user.getEmail(),
                user.getRole()
        );
        auditLogService.createLog("LOGIN_SUCCEEDED", "outcome=SUCCESS; authentication succeeded", null, user.getId());
        return token;
    }

    private void recordFailedLogin(User attemptedAccount) {
        // Link failures to a known account without recording the submitted email
        // or credential. Unknown account attempts remain unattributed.
        Long actorId = attemptedAccount == null ? null : attemptedAccount.getId();
        auditLogService.createLog("LOGIN_FAILED", "outcome=FAILURE; login credentials rejected", null, actorId);
    }
} 
