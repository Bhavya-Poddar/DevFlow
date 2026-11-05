import assert from "node:assert/strict";
import test from "node:test";
import { editablePermissionKeys, permissionKind, permissionsGroupedByModule } from "./rolePermissions.js";

const catalogue = [
    { key: "PROJECT_READ", scope: "PROJECT", description: "Read projects" },
    { key: "PROJECT_UPDATE", scope: "PROJECT", description: "Update projects" },
    { key: "TEAM_READ", scope: "PROJECT_TEAM", description: "Read team" },
    { key: "USER_READ", scope: "GLOBAL", description: "Read users" },
    { key: "ROLE_MANAGE", scope: "GLOBAL", description: "Protected recovery" },
    { key: "UNSUPPORTED", scope: "GLOBAL", description: "Not in the supported module map" }
];

test("role editor exposes only catalogue permissions valid for the selected scope", () => {
    assert.deepEqual(editablePermissionKeys(catalogue, "PROJECT"), ["PROJECT_READ", "PROJECT_UPDATE"]);
    assert.deepEqual(editablePermissionKeys(catalogue, "PROJECT_TEAM"), ["TEAM_READ"]);
    assert.ok(!editablePermissionKeys(catalogue, "GLOBAL").includes("ROLE_MANAGE"));
    assert.ok(!editablePermissionKeys(catalogue, "GLOBAL").includes("UNSUPPORTED"));
});

test("permission groups keep visibility separate from actions", () => {
    const groups = permissionsGroupedByModule(catalogue, "GLOBAL");
    const projects = groups.find(group => group.name === "Projects");
    assert.equal(permissionKind(projects.permissions.find(item => item.key === "PROJECT_READ")), "visibility");
    assert.equal(permissionKind(projects.permissions.find(item => item.key === "PROJECT_UPDATE")), "action");
});