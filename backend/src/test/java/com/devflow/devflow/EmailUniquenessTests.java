package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
class EmailUniquenessTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void publicRegistrationIsDisabledAndDoesNotCreateAccounts() throws Exception {
        String canonicalEmail = uniqueEmail();
        String firstEmail = "  " + canonicalEmail.toUpperCase() + "  ";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Normalized\",\"email\":\"" + firstEmail
                    + "\",\"password\":\"valid-password\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Duplicate\",\"email\":\"" + canonicalEmail.toUpperCase()
                    + "\",\"password\":\"valid-password\"}"))
            .andExpect(status().isForbidden());
        assertEquals(0L, userRepository.findAll().stream()
            .filter(user -> canonicalEmail.equalsIgnoreCase(user.getEmail())).count());
    }

    @Test
    void adminUserCreationNormalizesEmailAndReturnsConflictForDuplicate() throws Exception {
        User admin = user(uniqueEmail(), "ADMIN");
        String email = uniqueEmail();
        String body = "{\"name\":\"Created user\",\"email\":\" " + email.toUpperCase()
            + " \",\"password\":\"valid-password\",\"role\":\"VIEWER\"}";

        mockMvc.perform(post("/api/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email));
        mockMvc.perform(post("/api/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Duplicate\",\"email\":\"" + email.toUpperCase()
                    + "\",\"password\":\"valid-password\",\"role\":\"VIEWER\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void userUpdateNormalizesEmailAndRejectsAnotherAccountsNormalizedAddress() throws Exception {
        User admin = user(uniqueEmail(), "ADMIN");
        User target = user(uniqueEmail(), "VIEWER");
        User other = user(uniqueEmail(), "VIEWER");

        mockMvc.perform(put("/api/users/{id}", target.getId()).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  " + target.getEmail().toUpperCase() + "  \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(target.getEmail()));
        mockMvc.perform(put("/api/users/{id}", target.getId()).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" " + other.getEmail().toUpperCase() + " \"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void databaseUniqueConstraintRejectsDuplicateStoredAddress() {
        String email = uniqueEmail();
        user(email, "VIEWER");
        User duplicate = new User();
        duplicate.setEmail(email);
        duplicate.setName("Duplicate row");
        duplicate.setRole("VIEWER");
        duplicate.setPassword(passwordEncoder.encode("valid-password"));

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setAdminRecovery("ADMIN".equals(role));
        user.setPassword(passwordEncoder.encode("test-password"));
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getRole());
    }

    private String uniqueEmail() {
        return UUID.randomUUID().toString().replace("-", "") + "@phase2b.test";
    }
}
