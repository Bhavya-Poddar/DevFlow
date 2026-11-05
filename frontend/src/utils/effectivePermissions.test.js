import test from 'node:test';
import assert from 'node:assert/strict';
import { effectivePermissionSet, hasEffectiveAllPermissions, hasEffectiveAnyPermission, hasRecoveryCapability } from './effectivePermissions.js';

test('effective permissions include workspace and scoped grants', () => {
    const grants = effectivePermissionSet({
        global: ['USER_READ'],
        projects: [{ projectId: 12, permissions: ['ISSUE_READ'] }],
        teams: [{ teamId: 5, permissions: ['TEAM_ACTIVITY_READ'] }]
    });
    assert.deepEqual([...grants].sort(), ['ISSUE_READ', 'TEAM_ACTIVITY_READ', 'USER_READ']);
});

test('a scoped grant makes a module route available while missing grants deny access', () => {
    const current = { global: [], projects: [{ projectId: 12, permissions: ['TESTING_QUEUE_READ'] }], teams: [] };
    assert.equal(hasEffectiveAnyPermission(current, ['TESTING_QUEUE_READ']), true);
    assert.equal(hasEffectiveAnyPermission(current, ['USER_READ']), false);
    assert.equal(hasEffectiveAnyPermission(null, ['AUDIT_READ']), false);
    assert.equal(hasEffectiveAllPermissions(current, ['ISSUE_READ']), false);
    assert.equal(hasEffectiveAllPermissions(current, ['TESTING_QUEUE_READ']), true);
});

test('recovery access is independent from editable permission grants', () => {
    assert.equal(hasRecoveryCapability({ global: ['USER_MANAGE', 'PROJECT_ROLE_MANAGE'] }), false);
    assert.equal(hasRecoveryCapability({ recoveryCapable: true, global: [] }), true);
});
