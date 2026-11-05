const RESPONSIBILITY_ROLE = Object.freeze({
    PROJECT_MANAGER: "PROJECT_MANAGER",
    TEAM_LEAD: "TEAM_LEAD",
    DEVELOPER: "DEVELOPER",
    TESTER: "TESTER"
});

export function membersForResponsibility(users, responsibility) {
    const expectedRole = RESPONSIBILITY_ROLE[responsibility];
    if (!expectedRole || !Array.isArray(users)) return [];
    return users.filter(user => String(user?.role || "").trim().toUpperCase() === expectedRole);
}