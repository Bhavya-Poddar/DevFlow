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
import com.devflow.devflow.dto.UserRoleUpdateRequestDTO;
import com.devflow.devflow.dto.UserUpdateRequestDTO;
import com.devflow.devflow.dto.ProfileUpdateRequestDTO;
import com.devflow.devflow.dto.PasswordChangeRequestDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;


    // CREATE USER
    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(
            @RequestBody UserCreateRequestDTO userRequest) {

        User createdUser =
                userService.createUser(userRequest);

        return new ResponseEntity<>(toResponse(createdUser), HttpStatus.CREATED);
    }


    // GET ALL USERS - ADMIN ONLY
    @GetMapping
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
    public ResponseEntity<List<UserResponseDTO>> getAssignableUsers() {

        List<User> users =
                userService.getAllUsers();

        List<UserResponseDTO> response = users.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/testers")
    public ResponseEntity<List<UserResponseDTO>> getTesters() {
        List<UserResponseDTO> response = userService.getTesters().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }


    // GET CURRENT USER
    @GetMapping("/me")
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
    public ResponseEntity<UserResponseDTO> updateCurrentProfile(
            @RequestBody ProfileUpdateRequestDTO profile,
            Authentication authentication) {
        return ResponseEntity.ok(toResponse(
            userService.updateCurrentProfile(authentication.getName(), profile)));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeCurrentPassword(
            @RequestBody PasswordChangeRequestDTO request,
            Authentication authentication) {
        userService.changeCurrentPassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponseDTO> uploadCurrentAvatar(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(toResponse(
            userService.updateCurrentAvatar(authentication.getName(), file)));
    }

    @GetMapping("/{id}/avatar")
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
    public ResponseEntity<UserResponseDTO> getUserById(
            @PathVariable Long id) {

        Optional<User> user =
                userService.getUserById(id);

        if (user.isPresent()) {
            return ResponseEntity.ok(toResponse(user.get()));
        }

        return ResponseEntity.notFound().build();
    }


    // GET USER BY EMAIL
    @GetMapping("/email/{email}")
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
    public ResponseEntity<String> deleteUser(
            @PathVariable Long id) {

        userService.deleteUser(id);

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
                user.getAvatarUrl()
        );
    }
}
