package com.devflow.devflow.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.dto.UserCreateRequestDTO;
import com.devflow.devflow.dto.UserUpdateRequestDTO;
import com.devflow.devflow.dto.ProfileUpdateRequestDTO;
import com.devflow.devflow.dto.PasswordChangeRequestDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.util.EmailAddress;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationRoleRepository authorizationRoleRepository;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private EffectivePermissionService effectivePermissionService;

    @Autowired
    private AdminRecoveryGuard adminRecoveryGuard;

    @Autowired
    private AdminRecoveryCapability adminRecoveryCapability;

    @Autowired
    private ProjectTeamProvisioningService projectTeamProvisioningService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogServices auditLogService;

    @Autowired
    private ProfilePictureStorageService profilePictureStorageService;

    public User createUser(UserCreateRequestDTO userRequest) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "An authenticated user-management actor is required to create accounts");
    }

    @Transactional
    public User createUser(UserCreateRequestDTO userRequest, String actorEmail) {

        if (actorEmail == null || actorEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "An authenticated user-management actor is required to create accounts");
        }
        adminRecoveryGuard.lockInvariant();
        effectivePermissionService.require(actorEmail, "USER_MANAGE", null, null);
        // Account creation necessarily assigns an initial global role. Keep that privilege
        // behind the separate recovery capability just like later global-role changes.
        effectivePermissionService.require(actorEmail, "ROLE_MANAGE", null, null);

        String email = EmailAddress.normalize(userRequest.getEmail());
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        if (userRequest.getPassword() == null || userRequest.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        String requestedRole = userRequest.getRole();
        String roleCode = requestedRole == null ? "" : requestedRole.trim().toUpperCase(java.util.Locale.ROOT);
        AuthorizationRole configuredRole = authorizationRoleRepository.findByCodeAndRetiredFalse(roleCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is unavailable"));
        if (configuredRole.getScope() != PermissionScope.GLOBAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Initial account role must have GLOBAL scope");
        }
        String role = configuredRole.getCode();

        User user = new User();
        user.setName(userRequest.getName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
        user.setRole(role);

        User created;
        try {
            created = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        Long actorId = auditLogService.findActorId(actorEmail);
        auditLogService.createLog("USER_CREATED", "targetType=USER; targetId=" + created.getId()
                + "; administrator created account with role " + role, null, actorId);
        return created;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getTesters() {
        List<String> roleCodes = authorizationRoleRepository.findAll().stream()
            .filter(role -> role.getScope() == PermissionScope.GLOBAL)
            .filter(role -> role.getPermissions().stream().anyMatch(permission ->
                "ASSIGNABLE_TESTER".equals(permission.getKey())))
            .map(AuthorizationRole::getCode).toList();
        return roleCodes.isEmpty() ? List.of() : userRepository.findByRoleInAndEnabledTrue(roleCodes);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Transactional
    public User updateCurrentProfile(String authenticatedEmail, ProfileUpdateRequestDTO request) {
        adminRecoveryGuard.lockInvariant();
        User currentUser = currentUser(authenticatedEmail);
        boolean recoveryAccount = adminRecoveryCapability.hasCapability(currentUser);
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
        boolean nameChanged = !Objects.equals(currentUser.getName(), request.getName().trim());
        boolean emailChanged = !Objects.equals(currentUser.getEmail(), email);
        currentUser.setName(request.getName().trim());
        currentUser.setEmail(email);
        User saved;
        try {
            saved = userRepository.saveAndFlush(currentUser);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (recoveryAccount && !adminRecoveryCapability.hasCapability(saved)) {
            adminRecoveryGuard.requireRecoveryAccount();
        }
        if (nameChanged || emailChanged) {
            List<String> fields = new java.util.ArrayList<>();
            if (nameChanged) fields.add("name");
            if (emailChanged) fields.add("email");
            auditLogService.createLog("PROFILE_UPDATED", "targetType=USER; targetId=" + saved.getId()
                    + "; updatedFields=" + String.join(",", fields), null, saved.getId());
        }
        return saved;
    }

    @Transactional
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
        auditLogService.createLog("PASSWORD_CHANGED", "targetType=USER; targetId=" + currentUser.getId()
                + "; password changed", null, currentUser.getId());
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

    @Transactional
    public User updateUserRole(Long userId, String requestedRole, String actorEmail) {
        String roleCode = requestedRole == null ? "" : requestedRole.trim().toUpperCase(java.util.Locale.ROOT);
        AuthorizationRole role = authorizationRoleRepository.findByCodeAndRetiredFalse(roleCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is unavailable"));
        if (role.getScope() != PermissionScope.GLOBAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Global role assignment requires a GLOBAL role");
        }
        roleManagementService.assignGlobalRole(userId, role.getId(), actorEmail);
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public User updateUser(Long id, UserUpdateRequestDTO updatedUser, String actorEmail) {
        // Serialize account disablement with recovery-capability changes before reading the target.
        if (updatedUser != null && updatedUser.hasEnabled() && Boolean.FALSE.equals(updatedUser.getEnabled())) {
            adminRecoveryGuard.lockInvariant();
        }
        effectivePermissionService.require(actorEmail, "USER_MANAGE", null, null);

        // Resolve the actor before editing any account fields. An administrator
        // may update their own email, so a later lookup by the old email would fail.
        User actor = userRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<String> changedFields = new java.util.ArrayList<>();
        boolean wasRecoveryCapability = adminRecoveryCapability.hasCapability(existingUser);

        if (updatedUser.hasName()) {
            if (updatedUser.getName() == null || updatedUser.getName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be blank");
            }
            if (!Objects.equals(existingUser.getName(), updatedUser.getName())) changedFields.add("name");
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
            if (!Objects.equals(existingUser.getEmail(), email)) changedFields.add("email");
            existingUser.setEmail(email);
        }

        if (updatedUser.hasEnabled()) {
            if (updatedUser.getEnabled() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enabled must be true or false");
            }
            if (!updatedUser.getEnabled() && Objects.equals(actor.getId(), existingUser.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins cannot disable their own account");
            }
            if (!updatedUser.getEnabled() && existingUser.isEnabled()) {
                projectTeamProvisioningService.validateUserDeactivation(existingUser.getId());
            }
            if (existingUser.isEnabled() != updatedUser.getEnabled()) changedFields.add("enabled");
            existingUser.setEnabled(updatedUser.getEnabled());
        }

        User saved;
        try {
            saved = userRepository.saveAndFlush(existingUser);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (wasRecoveryCapability && !adminRecoveryCapability.hasCapability(saved)) {
            adminRecoveryGuard.requireRecoveryAccount();
        }
        if (!changedFields.isEmpty()) {
            auditLogService.createLog("USER_UPDATED", "targetType=USER; targetId=" + id
                    + "; updatedFields=" + String.join(",", changedFields), null, actor.getId());
        }
        return saved;
    }

    @Transactional
    public void deleteUser(Long id) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "An authenticated user-management actor is required to delete accounts");
    }

    @Transactional
    public void deleteUser(Long id, String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "An authenticated user-management actor is required to delete accounts");
        }
        // User deletion can remove a recovery account; serialize before loading it so no stale
        // account state is used to decide whether the last recovery-capable account may be removed.
        adminRecoveryGuard.lockInvariant();
        effectivePermissionService.require(actorEmail, "USER_MANAGE", null, null);
        User target = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Long actorId = actorEmail == null ? null : auditLogService.findActorId(actorEmail);
        if (actorId != null && Objects.equals(actorId, target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrators cannot delete their own account");
        }
        if (adminRecoveryCapability.hasCapability(target)) {
            userRepository.deleteById(id);
            userRepository.flush();
            adminRecoveryGuard.requireRecoveryAccount();
        } else {
            userRepository.deleteById(id);
        }
        if (actorId != null) {
            auditLogService.createLog("USER_DELETED", "targetType=USER; targetId=" + id + "; account deleted",
                    null, actorId);
        }
    }
}
