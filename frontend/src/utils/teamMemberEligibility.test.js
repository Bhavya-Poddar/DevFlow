import assert from "node:assert/strict";
import test from "node:test";
import { membersForResponsibility } from "./teamMemberEligibility.js";

test("filters team candidates to the exact requested global role", () => {
    const users = [
        { id: 1, name: "Manager", role: "PROJECT_MANAGER" },
        { id: 2, name: "Lead", role: "TEAM_LEAD" },
        { id: 3, name: "Developer", role: "DEVELOPER" },
        { id: 4, name: "Tester", role: "TESTER" },
        { id: 5, name: "Admin", role: "ADMIN" },
        { id: 6, name: "Partial", role: "TESTER_PROJECT" }
    ];
    assert.deepEqual(membersForResponsibility(users, "TESTER").map(user => user.id), [4]);
    assert.deepEqual(membersForResponsibility(users, "DEVELOPER").map(user => user.id), [3]);
    assert.deepEqual(membersForResponsibility(users, "TEAM_LEAD").map(user => user.id), [2]);
    assert.deepEqual(membersForResponsibility(users, "PROJECT_MANAGER").map(user => user.id), [1]);
    assert.deepEqual(membersForResponsibility(users, "UNKNOWN"), []);
});