package com.devflow.devflow.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.AuthorizationInvariantLock;
import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.PermissionDefinition;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.repository.AuthorizationInvariantLockRepository;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.PermissionDefinitionRepository;

/** H2/dev bootstrap; production migration V13 contains the same initial catalogue and grants. */
@Component
@Order(0)
public class AuthorizationDataInitializer implements ApplicationRunner {
    private final PermissionDefinitionRepository permissions;
    private final AuthorizationRoleRepository roles;
    private final AuthorizationInvariantLockRepository locks;

    public AuthorizationDataInitializer(PermissionDefinitionRepository permissions,
            AuthorizationRoleRepository roles, AuthorizationInvariantLockRepository locks) {
        this.permissions = permissions; this.roles = roles; this.locks = locks;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (PermissionCatalogue.Entry entry : PermissionCatalogue.entries()) {
            if (!permissions.existsById(entry.key())) {
                permissions.save(new PermissionDefinition(entry.key(), entry.description(), entry.scope()));
            }
        }
        Map<String, PermissionDefinition> definitions = permissions.findAll().stream()
            .collect(Collectors.toMap(PermissionDefinition::getKey, Function.identity()));
        for (RoleSeed seed : defaultRoles()) {
            if (roles.existsByCodeIgnoreCase(seed.code())) continue;
            AuthorizationRole role = new AuthorizationRole(seed.code(), seed.name(), seed.scope(), true);
            role.setPermissions(seed.keys().stream().map(definitions::get).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
            roles.save(role);
        }
        if (!locks.existsById(1)) locks.save(new AuthorizationInvariantLock(1));
    }

    private List<RoleSeed> defaultRoles() {
        Set<String> read = keys("PROJECT_READ", "ISSUE_READ", "COMMENT_READ", "REPORT_READ", "DASHBOARD_READ",
            "TESTING_HISTORY_READ");
        Set<String> admin = PermissionCatalogue.entries().stream().map(PermissionCatalogue.Entry::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> managerProject = keys("PROJECT_READ", "PROJECT_UPDATE", "PROJECT_DELETE", "ISSUE_READ",
            "PROJECT_ROLE_MANAGE", "ISSUE_CREATE", "ISSUE_UPDATE", "ISSUE_UPDATE_ALL", "ISSUE_ASSIGN_DEVELOPER",
            "ISSUE_STATUS_MANAGE", "ISSUE_DELETE", "TESTER_ASSIGN", "ISSUE_DEPENDENCY_MANAGE",
            "COMMENT_READ", "COMMENT_CREATE", "COMMENT_DELETE", "REPORT_READ",
            "DASHBOARD_READ", "AUDIT_READ", "AUTOMATION_MANAGE", "TESTING_QUEUE_READ",
            "TESTING_HISTORY_READ", "ISSUE_APPROVE_PROJECT_MANAGER");
        Set<String> teamLeadProject = keys("PROJECT_READ", "ISSUE_READ", "COMMENT_READ", "ISSUE_APPROVE_TEAM_LEAD");
        Set<String> developerProject = keys("PROJECT_READ", "ISSUE_READ", "ISSUE_CREATE", "ISSUE_UPDATE",
            "ISSUE_UPDATE_ASSIGNED", "ISSUE_STATUS_ADVANCE",
            "COMMENT_READ", "COMMENT_CREATE", "REPORT_READ", "DASHBOARD_READ", "TESTING_HISTORY_READ");
        Set<String> testerProject = keys("PROJECT_READ", "ISSUE_READ", "COMMENT_READ", "REPORT_READ",
            "DASHBOARD_READ", "TESTING_QUEUE_READ", "TESTING_MY_READ", "TESTING_HISTORY_READ", "TEST_EXECUTE");
        Set<String> viewerProject = keys("PROJECT_READ", "ISSUE_READ", "COMMENT_READ", "REPORT_READ",
            "DASHBOARD_READ", "TESTING_HISTORY_READ");
        return List.of(
            new RoleSeed("ADMIN", "Administrator", PermissionScope.GLOBAL, admin),
            new RoleSeed("PROJECT_MANAGER", "Project Manager", PermissionScope.GLOBAL,
                union(read, keys("PROJECT_CREATE", "TEAM_CREATE", "PROJECT_UPDATE", "PROJECT_DELETE",
                    "ISSUE_CREATE", "ISSUE_UPDATE", "ISSUE_UPDATE_ALL", "ISSUE_ASSIGN_DEVELOPER",
                    "ISSUE_STATUS_MANAGE", "ISSUE_DELETE", "TESTER_ASSIGN", "ISSUE_DEPENDENCY_MANAGE",
                    "COMMENT_CREATE", "COMMENT_DELETE", "AUDIT_READ",
                    "AUTOMATION_MANAGE", "TESTING_QUEUE_READ"))),
            new RoleSeed("DEVELOPER", "Developer", PermissionScope.GLOBAL,
                union(read, keys("ISSUE_CREATE", "ISSUE_UPDATE", "ISSUE_UPDATE_ASSIGNED", "ISSUE_STATUS_ADVANCE",
                    "COMMENT_CREATE", "ASSIGNABLE_DEVELOPER"))),
            new RoleSeed("TESTER", "Tester", PermissionScope.GLOBAL,
                union(read, keys("TESTING_MY_READ", "TESTING_QUEUE_READ", "TEST_EXECUTE", "ASSIGNABLE_TESTER"))),
            new RoleSeed("VIEWER", "Viewer", PermissionScope.GLOBAL, read),
            new RoleSeed("PROJECT_MANAGER_PROJECT", "Project Manager (Project)", PermissionScope.PROJECT, managerProject),
            new RoleSeed("DEVELOPER_PROJECT", "Developer (Project)", PermissionScope.PROJECT, developerProject),
            new RoleSeed("TESTER_PROJECT", "Tester (Project)", PermissionScope.PROJECT, testerProject),
            new RoleSeed("VIEWER_PROJECT", "Viewer (Project)", PermissionScope.PROJECT, viewerProject),
            new RoleSeed("PROJECT_MANAGER_TEAM", "Project Manager (Team)", PermissionScope.PROJECT_TEAM,
                keys("TEAM_READ", "TEAM_MANAGE", "TEAM_ACTIVITY_READ", "TEAM_ROLE_MANAGE")),
            new RoleSeed("TEAM_LEAD_PROJECT", "Team Lead (Project)", PermissionScope.PROJECT, teamLeadProject),
            new RoleSeed("TEAM_LEAD_TEAM", "Team Lead", PermissionScope.PROJECT_TEAM,
                keys("TEAM_READ", "TEAM_ACTIVITY_READ")),
            new RoleSeed("DEVELOPER_TEAM", "Developer (Team)", PermissionScope.PROJECT_TEAM,
                keys("TEAM_READ", "TEAM_ACTIVITY_READ")),
            new RoleSeed("TESTER_TEAM", "Tester (Team)", PermissionScope.PROJECT_TEAM,
                keys("TEAM_READ", "TEAM_ACTIVITY_READ")),
            new RoleSeed("VIEWER_TEAM", "Viewer (Team)", PermissionScope.PROJECT_TEAM,
                keys("TEAM_READ", "TEAM_ACTIVITY_READ"))
        );
    }

    private Set<String> keys(String... values) { return new LinkedHashSet<>(List.of(values)); }
    private Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first); result.addAll(second); return result;
    }
    private record RoleSeed(String code, String name, PermissionScope scope, Set<String> keys) {}
}
