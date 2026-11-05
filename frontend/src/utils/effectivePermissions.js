export function effectivePermissionSet(permissionData) {
    return new Set([
        ...(Array.isArray(permissionData?.global) ? permissionData.global : []),
        ...(Array.isArray(permissionData?.projects) ? permissionData.projects.flatMap(scope => scope?.permissions || []) : []),
        ...(Array.isArray(permissionData?.teams) ? permissionData.teams.flatMap(scope => scope?.permissions || []) : [])
    ]);
}

export function hasEffectiveAnyPermission(permissionData, required = []) {
    if (!Array.isArray(required) || required.length === 0) return true;
    const granted = effectivePermissionSet(permissionData);
    return required.some(permission => granted.has(permission));
}

export function hasEffectiveAllPermissions(permissionData, required = []) {
    if (!Array.isArray(required) || required.length === 0) return true;
    const granted = effectivePermissionSet(permissionData);
    return required.every(permission => granted.has(permission));
}

export function hasRecoveryCapability(permissionData) {
    return permissionData?.recoveryCapable === true;
}
