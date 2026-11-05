package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class JwtAuthorizationFreshnessTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Value("${devflow.jwt.secret}") private String testSigningSecret;

    @Test
    void tokenRoleClaimCannotGrantMoreAccessThanCurrentDatabaseRole() throws Exception {
        User developer = user("stale-admin@example.test", "DEVELOPER");
        String oldElevatedToken = jwtService.generateToken(developer.getEmail(), "ADMIN");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(oldElevatedToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void roleDemotionTakesEffectForAnAlreadyIssuedToken() throws Exception {
        User actingAdmin = user("demotion-actor@example.test", "ADMIN");
        User targetAdmin = user("demoted-admin@example.test", "ADMIN");
        String oldAdminToken = jwtService.generateToken(targetAdmin.getEmail(), "ADMIN");

        mockMvc.perform(put("/api/users/{id}/role", targetAdmin.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(actingAdmin.getEmail(), "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(oldAdminToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void rolePromotionTakesEffectWithoutIssuingANewToken() throws Exception {
        User actingAdmin = user("promotion-actor@example.test", "ADMIN");
        User viewer = user("promoted-viewer@example.test", "VIEWER");
        String oldViewerToken = jwtService.generateToken(viewer.getEmail(), "VIEWER");

        mockMvc.perform(put("/api/users/{id}/role", viewer.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(actingAdmin.getEmail(), "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(oldViewerToken)))
            .andExpect(status().isOk());
    }

    @Test
    void deletedAndDisabledAccountsCannotUsePreviouslyIssuedTokens() throws Exception {
        User deleted = user("deleted-user@example.test", "ADMIN");
        String deletedToken = jwtService.generateToken(deleted.getEmail(), "ADMIN");
        userRepository.delete(deleted);
        userRepository.flush();

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(deletedToken)))
            .andExpect(status().isUnauthorized());

        User disabled = user("disabled-user@example.test", "ADMIN");
        String disabledToken = jwtService.generateToken(disabled.getEmail(), "ADMIN");
        disabled.setEnabled(false);
        userRepository.saveAndFlush(disabled);

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(disabledToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureAndExpiredTokensAreRejected() throws Exception {
        User user = user("bad-token-user@example.test", "ADMIN");
        String wrongKeyToken = Jwts.builder()
            .subject(user.getEmail())
            .claim("role", "ADMIN")
            .issuedAt(new Date(System.currentTimeMillis() - 10_000))
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor("different-signing-key-for-test-12345".getBytes(StandardCharsets.UTF_8)))
            .compact();
        String expiredToken = Jwts.builder()
            .subject(user.getEmail())
            .claim("role", "ADMIN")
            .issuedAt(new Date(System.currentTimeMillis() - 120_000))
            .expiration(new Date(System.currentTimeMillis() - 60_000))
            .signWith(Keys.hmacShaKeyFor(testSigningSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(wrongKeyToken)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validNonAdminTokenStillGetsForbiddenFromAdminEndpoint() throws Exception {
        User developer = user("regular-developer@example.test", "DEVELOPER");
        String token = jwtService.generateToken(developer.getEmail(), "DEVELOPER");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDisableAnotherAccountAndItsExistingTokenStopsWorking() throws Exception {
        User admin = user("disable-actor@example.test", "ADMIN");
        User developer = user("disabled-by-admin@example.test", "DEVELOPER");
        String existingToken = jwtService.generateToken(developer.getEmail(), "DEVELOPER");

        mockMvc.perform(put("/api/users/{id}", developer.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(admin.getEmail(), "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/issues").header(HttpHeaders.AUTHORIZATION, bearer(existingToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDisableTheirOwnAccount() throws Exception {
        User admin = user("self-disable-admin@example.test", "ADMIN");

        mockMvc.perform(put("/api/users/{id}", admin.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(admin.getEmail(), "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isForbidden());
        assertTrue(userRepository.findById(admin.getId()).orElseThrow().isEnabled());
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setPassword("not-used-by-auth-filter");
        user.setRole(role);
        if ("ADMIN".equals(role)) user.setAdminRecovery(true);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
