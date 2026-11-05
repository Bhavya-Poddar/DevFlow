package com.devflow.devflow.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import com.devflow.devflow.dto.UserCreateRequestDTO;
import com.devflow.devflow.dto.UserResponseDTO;
import com.devflow.devflow.dto.CollaboratorResponseDTO;
import com.devflow.devflow.dto.UserRoleUpdateRequestDTO;
import com.devflow.devflow.dto.UserUpdateRequestDTO;
import com.devflow.devflow.dto.ProfileUpdateRequestDTO;
import com.devflow.devflow.dto.PasswordChangeRequestDTO;
import com.devflow.devflow.dto.UserMembershipsResponseDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.service.UserService;
import com.devflow.devflow.service.UserMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User directory, profile, role, and avatar operations.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMembershipService userMembershipService;


    // CREATE USER
    @PostMapping
    @Operation(summary = "Create a user", description = "Creates a user through the administrator-only user-management flow.")
    @ApiResponse(responseCode = "201", description = "User created; returns a safe user summary.")
    public ResponseEntity<UserResponseDTO> createUser(
            @RequestBody UserCreateRequestDTO userRequest, Authentication authentication) {

        User createdUser =
                userService.createUser(userRequest, authentication.getName());

        return new ResponseEntity<>(toResponse(createdUser), HttpStatus.CREATED);
    }


    // GET ALL USERS - ADMIN ONLY
    @GetMapping
    @Operation(summary = "List users", description = "Returns safe user summaries. Administrator access is required.")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {

        List<User> users =
                userService.getAllUsers();

        List<UserResponseDTO> response = users.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }


    // GET USERS FOR ISSUE ASSIGNMENT
    @GetMapping("/assignable")
    @Operation(summary = "List assignable users", description = "Returns safe user summaries used by task assignment controls.")
    public ResponseEntity<List<CollaboratorResponseDTO>> getAssignableUsers() {

        List<User> users =
                userService.getAllUsers().stream().filter(User::isEnabled).toList();

        List<CollaboratorResponseDTO> response = users.stream()
                .map(CollaboratorResponseDTO::new)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/testers")
    @Operation(summary = "List testers", description = "Returns users whose role is TESTER.")
    public ResponseEntity<List<CollaboratorResponseDTO>> getTesters() {
        List<CollaboratorResponseDTO> response = userService.getTesters().stream()
                .map(CollaboratorResponseDTO::new)
                .toList();
        return ResponseEntity.ok(response);
    }


    // GET CURRENT USER
    @GetMapping("/me")
    @Operation(summary = "Get my profile", description = "Returns the authenticated user's safe profile summary.")
    public ResponseEntity<UserResponseDTO> getCurrentUser(
            Authentication authentication) {

        String email = authentication.getName();

        Optional<User> user =
                userService.getUserByEmail(email);

        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User currentUser = user.get();

        return ResponseEntity.ok(toResponse(currentUser));
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Update my profile", description = "Updates the authenticated user's display name and email.")
    public ResponseEntity<UserResponseDTO> updateCurrentProfile(
            @RequestBody ProfileUpdateRequestDTO profile,
            Authentication authentication) {
        return ResponseEntity.ok(toResponse(
            userService.updateCurrentProfile(authentication.getName(), profile)));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change my password", description = "Changes the authenticated user's password after verifying the current password.")
    @ApiResponse(responseCode = "204", description = "Password changed.")
    public ResponseEntity<Void> changeCurrentPassword(
            @RequestBody PasswordChangeRequestDTO request,
            Authentication authentication) {
        userService.changeCurrentPassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload my avatar", description = "Uploads an image for the authenticated user's profile.")
    public ResponseEntity<UserResponseDTO> uploadCurrentAvatar(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(toResponse(
            userService.updateCurrentAvatar(authentication.getName(), file)));
    }

    @GetMapping("/{id}/avatar")
    @Operation(summary = "Get a user avatar", description = "Returns the stored avatar image for a user when present.")
    public ResponseEntity<Resource> getAvatar(@PathVariable Long id) {
        UserService.ResourceWithType avatar = userService.getAvatar(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(avatar.contentType()))
            .cacheControl(CacheControl.noCache().cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .body(avatar.resource());
    }


    // GET USER BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get a user", description = "Returns a safe user summary by ID. Administrator access is required.")
    public ResponseEntity<UserResponseDTO> getUserById(
            @PathVariable Long id) {

        Optional<User> user =
                userService.getUserById(id);

        if (user.isPresent()) {
            return ResponseEntity.ok(toResponse(user.get()));
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/memberships")
    @Operation(summary = "Get a user's project and team memberships", description = "Returns scoped memberships to authorized user managers.")
    public ResponseEntity<UserMembershipsResponseDTO> getMemberships(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userMembershipService.getMemberships(id, authentication.getName()));
    }


    // GET USER BY EMAIL
    @GetMapping("/email/{email}")
    @Operation(summary = "Find a user by email", description = "Returns a safe user summary for the requested email. Administrator access is required.")
    public ResponseEntity<UserResponseDTO> getUserByEmail(
            @PathVariable String email) {

        Optional<User> user =
                userService.getUserByEmail(email);

        if (user.isPresent()) {
            return ResponseEntity.ok(toResponse(user.get()));
        }

        return ResponseEntity.notFound().build();
    }


    // UPDATE USER
    @PutMapping("/{id}")
    @Operation(summary = "Update a user", description = "Updates allowed user-management fields. Administrator access is required.")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateRequestDTO updatedUser,
            Authentication authentication) {

        User user =
                userService.updateUser(
                        id,
                        updatedUser,
                        authentication.getName()
                );

        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Update a user's role", description = "Changes a user's role to an allowed DevFlow role. Administrator access is required.")
    public ResponseEntity<UserResponseDTO> updateUserRole(
            @PathVariable Long id,
            @RequestBody UserRoleUpdateRequestDTO roleRequest,
            Authentication authentication) {

        User updatedUser = userService.updateUserRole(
                id,
                roleRequest.getRole(),
                authentication.getName());

        return ResponseEntity.ok(toResponse(updatedUser));
    }


    // DELETE USER
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user", description = "Deletes a user when existing relationship and authorization rules allow it.")
    public ResponseEntity<String> deleteUser(
            @PathVariable Long id, Authentication authentication) {

        userService.deleteUser(id, authentication.getName());

        return ResponseEntity.ok(
                "User deleted successfully"
        );
    }

    private UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getAvatarUrl(),
                user.isEnabled()
        );
    }
}
