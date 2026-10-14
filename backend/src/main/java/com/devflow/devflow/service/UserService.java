package com.devflow.devflow.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;

import com.devflow.devflow.dto.UserCreateRequestDTO;
import com.devflow.devflow.dto.UserUpdateRequestDTO;
import com.devflow.devflow.dto.ProfileUpdateRequestDTO;
import com.devflow.devflow.dto.PasswordChangeRequestDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.util.EmailAddress;

@Service
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN",
            "PROJECT_MANAGER",
            "DEVELOPER",
            "VIEWER",
            "TESTER"
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogServices auditLogService;

    @Autowired
    private ProfilePictureStorageService profilePictureStorageService;

    public User createUser(UserCreateRequestDTO userRequest) {

        String email = EmailAddress.normalize(userRequest.getEmail());
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        if (userRequest.getPassword() == null || userRequest.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }

        String requestedRole = userRequest.getRole();
        String role = requestedRole == null
                ? ""
                : requestedRole.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(role)) {
            throw new RuntimeException("Role must be ADMIN, PROJECT_MANAGER, DEVELOPER, TESTER, or VIEWER");
        }

        User user = new User();
        user.setName(userRequest.getName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
        user.setRole(role);

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getTesters() {
        return userRepository.findByRole("TESTER");
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    public User updateCurrentProfile(String authenticatedEmail, ProfileUpdateRequestDTO request) {
        User currentUser = currentUser(authenticatedEmail);
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name cannot be blank");
        }
        String email = EmailAddress.normalize(request.getEmail());
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be blank");
        }
        if (!email.equals(currentUser.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        currentUser.setName(request.getName().trim());
        currentUser.setEmail(email);
        try {
            return userRepository.saveAndFlush(currentUser);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    public void changeCurrentPassword(String authenticatedEmail, PasswordChangeRequestDTO request) {
        User currentUser = currentUser(authenticatedEmail);
        if (request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "New password must be at least 12 characters long");
        }
        if (passwordEncoder.matches(request.getNewPassword(), currentUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a password different from the current one");
        }
        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);
    }

    public User updateCurrentAvatar(String authenticatedEmail, MultipartFile image) {
        User currentUser = currentUser(authenticatedEmail);
        String newKey = profilePictureStorageService.store(image);
        String oldKey = currentUser.getAvatarKey();
        currentUser.setAvatarKey(newKey);
        try {
            User saved = userRepository.saveAndFlush(currentUser);
            if (oldKey != null) profilePictureStorageService.delete(oldKey);
            return saved;
        } catch (RuntimeException exception) {
            profilePictureStorageService.delete(newKey);
            throw exception;
        }
    }

    public ResourceWithType getAvatar(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found"));
        if (user.getAvatarKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found");
        }
        return new ResourceWithType(profilePictureStorageService.load(user.getAvatarKey()),
            profilePictureStorageService.contentType(user.getAvatarKey()));
    }

    private User currentUser(String authenticatedEmail) {
        return userRepository.findByEmailIgnoreCase(authenticatedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public record ResourceWithType(org.springframework.core.io.Resource resource, String contentType) {}

    public User updateUserRole(Long userId, String requestedRole, String actorEmail) {
        String role = normalizeRole(requestedRole);
        if (!ALLOWED_ROLES.contains(role)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role must be ADMIN, PROJECT_MANAGER, DEVELOPER, TESTER, or VIEWER");
        }

        User actor = userRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (Objects.equals(actor.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins cannot change their own role");
        }

        String oldRole = target.getRole();
        if (!Objects.equals(oldRole, role)) {
            target.setRole(role);
            target = userRepository.save(target);

            auditLogService.createLog(
                    "ROLE_CHANGED",
                    "User '" + target.getName() + "' role changed from '" + oldRole + "' to '" + role + "'",
                    null,
                    actor.getId());
        }

        return target;
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    public User updateUser(Long id, UserUpdateRequestDTO updatedUser, String actorEmail) {

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (updatedUser.hasName()) {
            if (updatedUser.getName() == null || updatedUser.getName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be blank");
            }
            existingUser.setName(updatedUser.getName());
        }

        if (updatedUser.hasEmail()) {
            String email = EmailAddress.normalize(updatedUser.getEmail());
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be blank");
            }
            if (!email.equals(existingUser.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
            }
            existingUser.setEmail(email);
        }

        if (updatedUser.hasEnabled()) {
            if (updatedUser.getEnabled() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enabled must be true or false");
            }
            User actor = userRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
            if (!updatedUser.getEnabled() && Objects.equals(actor.getId(), existingUser.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins cannot disable their own account");
            }
            existingUser.setEnabled(updatedUser.getEnabled());
        }

        try {
            return userRepository.saveAndFlush(existingUser);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    public void deleteUser(Long id) {

        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found");
        }

        userRepository.deleteById(id);
    }
}
