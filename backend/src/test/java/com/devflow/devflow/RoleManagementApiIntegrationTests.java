package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.PermissionDefinitionRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class RoleManagementApiIntegrationTests {
    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository users;
    @Autowired private AuthorizationRoleRepository roles;
    @Autowired private PermissionDefinitionRepository permissionDefinitions;
    @Autowired private JwtService jwt;

    private User recoveryAdmin;
    private String token;

    @BeforeEach
    void setUp() {
        recoveryAdmin = new User();
        recoveryAdmin.setName("Recovery admin");
        recoveryAdmin.setEmail(UUID.randomUUID() + "@role-api.test");
        recoveryAdmin.setPassword("not-used-by-this-test");
        recoveryAdmin.setRole("ADMIN");
        recoveryAdmin.setEnabled(true);
        recoveryAdmin.setAdminRecovery(true);
        recoveryAdmin = users.saveAndFlush(recoveryAdmin);
        token = "Bearer " + jwt.generateToken(recoveryAdmin.getEmail(), recoveryAdmin.getRole());
    }

    @Test
    void addEditAndRetireRoleUseProtectedRoleApiWithoutChangingAssignments() throws Exception {
        String code = "UI_ROLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String createBody = objectMapper.writeValueAsString(Map.of(
            "code", code,
            "name", "Quality Coordinator",
            "scope", "GLOBAL",
            "permissions", java.util.List.of(),
            "description", "Before edit"));

        String createResponse = mockMvc.perform(post("/api/admin/roles")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.name").value("Quality Coordinator"))
            .andExpect(jsonPath("$.scope").value("GLOBAL"))
            .andReturn().getResponse().getContentAsString();
        long roleId = objectMapper.readTree(createResponse).get("id").asLong();

        String updateBody = objectMapper.writeValueAsString(Map.of(
            "code", code,
            "name", "Quality Lead",
            "scope", "GLOBAL",
            "permissions", java.util.List.of(),
            "description", "After edit"));
        mockMvc.perform(put("/api/admin/roles/{id}", roleId)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Quality Lead"))
            .andExpect(jsonPath("$.scope").value("GLOBAL"))
            .andExpect(jsonPath("$.description").value("After edit"))
            .andExpect(jsonPath("$.permissions").isEmpty());

        mockMvc.perform(get("/api/admin/roles").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$[?(@.id == " + roleId + ")].name").value(org.hamcrest.Matchers.contains("Quality Lead")));

        mockMvc.perform(delete("/api/admin/roles/{id}", roleId).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retired").value(true));
        org.junit.jupiter.api.Assertions.assertTrue(roles.findById(roleId).orElseThrow().isRetired());
    }

    @Test
    void roleCreationAndEditingRequireProtectedRecoveryCapability() throws Exception {
        User ordinaryAdmin = new User();
        ordinaryAdmin.setName("Ordinary role admin");
        ordinaryAdmin.setEmail(UUID.randomUUID() + "@role-api.test");
        ordinaryAdmin.setPassword("not-used-by-this-test");
        ordinaryAdmin.setRole("ADMIN");
        ordinaryAdmin.setEnabled(true);
        ordinaryAdmin.setAdminRecovery(false);
        ordinaryAdmin = users.saveAndFlush(ordinaryAdmin);
        String ordinaryToken = "Bearer " + jwt.generateToken(ordinaryAdmin.getEmail(), ordinaryAdmin.getRole());

        mockMvc.perform(post("/api/admin/roles")
                .header("Authorization", ordinaryToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"UNAUTHORIZED_ROLE\",\"name\":\"Nope\",\"scope\":\"GLOBAL\",\"permissions\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void roleApiNeverExposesRecoveryGrantAndRoleEditsImmediatelyChangeAssignedUsers() throws Exception {
        var catalogue = mockMvc.perform(get("/api/admin/roles/permissions").header("Authorization", token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(objectMapper.readTree(catalogue).findValuesAsText("key")
            .contains("ROLE_MANAGE"));

        var roleList = mockMvc.perform(get("/api/admin/roles").header("Authorization", token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var listedRole : objectMapper.readTree(roleList)) {
            for (var permission : listedRole.path("permissions")) {
                org.junit.jupiter.api.Assertions.assertNotEquals("ROLE_MANAGE", permission.asText());
            }
        }

        // Simulate a legacy join row. Editing ordinary grants must preserve that row,
        // while the API never exposes it and the independent recovery flag remains authoritative.
        var adminRole = roles.findByCode("ADMIN").orElseThrow();
        var recoveryDefinition = permissionDefinitions.findById("ROLE_MANAGE").orElseGet(() ->
            permissionDefinitions.saveAndFlush(new com.devflow.devflow.entity.PermissionDefinition(
                "ROLE_MANAGE", "Protected recovery", com.devflow.devflow.entity.PermissionScope.GLOBAL)));
        adminRole.getPermissions().add(recoveryDefinition);
        roles.saveAndFlush(adminRole);
        mockMvc.perform(put("/api/admin/roles/{id}", adminRole.getId())
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ADMIN\",\"name\":\"Administrator\",\"scope\":\"GLOBAL\",\"permissions\":[\"USER_READ\"]}"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.permissions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("ROLE_MANAGE"))));
        org.junit.jupiter.api.Assertions.assertTrue(roles.findByCode("ADMIN").orElseThrow().getPermissions().stream()
            .anyMatch(permission -> "ROLE_MANAGE".equals(permission.getKey())));
        org.junit.jupiter.api.Assertions.assertTrue(recoveryAdmin.hasAdminRecovery());

        String code = "DYNAMIC_ROLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String createBody = objectMapper.writeValueAsString(Map.of("code", code, "name", "Dynamic Reader",
            "scope", "GLOBAL", "permissions", java.util.List.of("USER_READ")));
        String roleResponse = mockMvc.perform(post("/api/admin/roles").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long roleId = objectMapper.readTree(roleResponse).get("id").asLong();

        User member = new User();
        member.setName("Dynamic role member");
        member.setEmail(UUID.randomUUID() + "@role-api.test");
        member.setPassword("not-used-by-this-test");
        member.setRole("VIEWER");
        member.setEnabled(true);
        member = users.saveAndFlush(member);
        String memberToken = "Bearer " + jwt.generateToken(member.getEmail(), member.getRole());
        mockMvc.perform(put("/api/users/{id}/role", member.getId()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("role", code))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me/permissions").header("Authorization", memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.global").value(org.hamcrest.Matchers.hasItem("USER_READ")));

        mockMvc.perform(put("/api/admin/roles/{id}", roleId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "name", "Dynamic Reader",
                    "scope", "GLOBAL", "permissions", java.util.List.of()))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me/permissions").header("Authorization", memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.global").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("USER_READ"))));
    }
}
