package com.devflow.devflow;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileAccountIntegrationTests {
    @TempDir static Path uploadDirectory;

    @DynamicPropertySource
    static void profileImageStorage(DynamicPropertyRegistry registry) {
        registry.add("devflow.profile-pictures.directory", () -> uploadDirectory.toString());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void profileUpdateIsBoundToAuthenticatedUserAndReturnsSafeDto() throws Exception {
        User user = saveUser("ADMIN");
        String token = token(user);

        mockMvc.perform(put("/api/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Name\",\"email\":\"Updated@Example.Test\",\"role\":\"VIEWER\",\"id\":999}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.email").value("updated@example.test"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.id").value(user.getId()))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("password"))));
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndStoresOnlyEncodedValue() throws Exception {
        User user = saveUser("DEVELOPER");
        String token = token(user);

        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"incorrect password\",\"newPassword\":\"correct horse battery\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"old password\",\"newPassword\":\"correct horse battery\"}"))
            .andExpect(status().isNoContent());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("correct horse battery", updated.getPassword()));
        org.junit.jupiter.api.Assertions.assertNotEquals("correct horse battery", updated.getPassword());
    }

    @Test
    void authenticatedUserCanUploadAndReadValidatedAvatarWithoutExposingStoragePath() throws Exception {
        User user = saveUser("VIEWER");
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jXioAAAAASUVORK5CYII=");
        MockMultipartFile image = new MockMultipartFile("file", "profile.png", "image/png", png);

        String response = mockMvc.perform(multipart("/api/users/me/avatar")
                .file(image)
                .header("Authorization", "Bearer " + token(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").value("/api/users/" + user.getId() + "/avatar"))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("avatarKey"));

        mockMvc.perform(get("/api/users/{id}/avatar", user.getId())
                .header("Authorization", "Bearer " + token(user)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(content().bytes(png));
    }

    @Test
    void invalidImageTypeIsRejected() throws Exception {
        User user = saveUser("VIEWER");
        MockMultipartFile image = new MockMultipartFile("file", "profile.svg", "image/svg+xml", "<svg/>".getBytes());
        mockMvc.perform(multipart("/api/users/me/avatar").file(image)
                .header("Authorization", "Bearer " + token(user)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void spoofedImageHeaderAndOversizedImageAreRejected() throws Exception {
        User user = saveUser("VIEWER");
        MockMultipartFile invalidPng = new MockMultipartFile("file", "profile.png", "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        mockMvc.perform(multipart("/api/users/me/avatar").file(invalidPng)
                .header("Authorization", "Bearer " + token(user)))
            .andExpect(status().isBadRequest());

        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile tooLarge = new MockMultipartFile("file", "large.png", "image/png", oversized);
        mockMvc.perform(multipart("/api/users/me/avatar").file(tooLarge)
                .header("Authorization", "Bearer " + token(user)))
            .andExpect(status().isPayloadTooLarge());
    }

    @AfterEach
    void removeTemporaryImages() throws IOException {
        if (Files.isDirectory(uploadDirectory)) {
            try (var files = Files.list(uploadDirectory)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
        }
    }

    private User saveUser(String role) {
        User user = new User();
        user.setName("Profile " + role);
        user.setEmail(UUID.randomUUID().toString().replace("-", "") + "@profile.test");
        user.setRole(role);
        user.setPassword(passwordEncoder.encode("old password"));
        return userRepository.saveAndFlush(user);
    }

    private String token(User user) {
        return jwtService.generateToken(user.getEmail(), user.getRole());
    }
}
