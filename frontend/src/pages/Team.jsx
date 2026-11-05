import { apiUrl } from "../services/apiConfig";
import { useCallback, useEffect, useMemo, useState } from "react";
import Avatar from "../components/Avatar";
import RecordTable from "../components/RecordTable";

function Team() {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [search, setSearch] = useState("");
    const [roleFilter, setRoleFilter] = useState("ALL");
    const [changingUser, setChangingUser] = useState(null);
    const [selectedRole, setSelectedRole] = useState("");
    const [success, setSuccess] = useState("");
    const [canManageRoles, setCanManageRoles] = useState(false);
    const [globalRoles, setGlobalRoles] = useState([]);
    const [currentUser, setCurrentUser] = useState(null);
    const [savingRole, setSavingRole] = useState(false);

    const token = localStorage.getItem("token");

    const loadUsers = useCallback(async () => {
        try {
            setLoading(true);
            setError("");

            const headers = { Authorization: `Bearer ${token}` };
            const [response, meResponse, permissionResponse] = await Promise.all([
                fetch(apiUrl("/api/users"), { headers }),
                fetch(apiUrl("/api/users/me"), { headers }),
                fetch(apiUrl("/api/users/me/permissions"), { headers })
            ]);

            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error("You do not have permission to manage users.");
                }
                throw new Error("Failed to load users.");
            }

            const data = await response.json();
            setUsers(Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : []);
            if (meResponse.ok) setCurrentUser(await meResponse.json());
            if (permissionResponse.ok) {
                const effective = await permissionResponse.json();
                const allowed = Boolean(effective?.recoveryCapable || effective?.global?.includes("ROLE_MANAGE"));
                setCanManageRoles(allowed);
                if (allowed) {
                    const roleResponse = await fetch(apiUrl("/api/admin/roles"), { headers });
                    if (roleResponse.ok) setGlobalRoles((await roleResponse.json()).filter(role => role.scope === "GLOBAL" && !role.retired));
                }
            }
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }, [token]);

    useEffect(() => {
        void Promise.resolve().then(loadUsers);
    }, [loadUsers]);

    const filteredUsers = useMemo(() => {
        return users.filter(user => {
            const matchesSearch =
                user.name?.toLowerCase().includes(search.toLowerCase()) ||
                user.email?.toLowerCase().includes(search.toLowerCase());

            const matchesRole =
                roleFilter === "ALL" || user.role === roleFilter;

            return matchesSearch && matchesRole;
        });
    }, [users, search, roleFilter]);

    const roleStyles = {
        ADMIN: "bg-red-500/10 text-red-400 border-red-500/20",
        PROJECT_MANAGER: "bg-purple-500/10 text-purple-400 border-purple-500/20",
        DEVELOPER: "bg-green-500/10 text-green-400 border-green-500/20",
        TESTER: "bg-blue-500/10 text-blue-400 border-blue-500/20",
        VIEWER: "bg-gray-500/10 text-gray-400 border-gray-500/20"
    };

    const handleOpenRoleChange = user => {
        if (!canManageRoles) return;
        setChangingUser(user);
        setSelectedRole(user.role);
        setError("");
        setSuccess("");
    };

    const handleRoleChange = async () => {
        if (!canManageRoles || !changingUser || !selectedRole) return;

        if (changingUser.role === selectedRole) {
            setChangingUser(null);
            return;
        }

        try {
            setSavingRole(true);
            setError("");
            setSuccess("");

            const response = await fetch(
                apiUrl(`/api/users/${changingUser.id}/role`),
                {
                    method: "PUT",
                    headers: {
                        Authorization: `Bearer ${token}`,
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        role: selectedRole
                    })
                }
            );

            const data = await response.json().catch(() => null);

            if (!response.ok) {
                throw new Error(data?.message || data?.error || "Failed to update role.");
            }

            const updatedUser = data || { ...changingUser, role: selectedRole };

            setUsers(prev =>
                prev.map(user =>
                    user.id === changingUser.id ? updatedUser : user
                )
            );

            setSuccess(
                `${changingUser.name}'s role was changed to ${roleName(selectedRole, globalRoles)}.`
            );

            setChangingUser(null);
        } catch (err) {
            setError(err.message);
        } finally {
            setSavingRole(false);
        }
    };

const totalAdmins = users.filter(user => user.role === "ADMIN").length;
    const totalManagers = users.filter(
        user => user.role === "PROJECT_MANAGER"
    ).length;
    const totalDevelopers = users.filter(
        user => user.role === "DEVELOPER"
    ).length;
    const totalTesters = users.filter(user => user.role === "TESTER").length;
    const totalViewers = users.filter(user => user.role === "VIEWER").length;

    if (loading) {
        return (
            <div className="flex min-h-[60vh] items-center justify-center">
                <div className="text-center">
                    <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-gray-700 border-t-blue-500" />
                    <p className="mt-4 text-sm text-gray-500">
                        Loading users...
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="dfx-screen dfx-screen--team space-y-8">
            <div className="dfx-screen-header">
                <div><p className="mb-2 text-xs font-semibold uppercase tracking-[.16em] text-violet-300">ACCESS / DIRECTORY</p><p className="text-sm font-medium text-red-400">
                    ADMINISTRATION
                </p>
                <h1 className="mt-1 text-3xl font-bold text-white">
                    User Management
                </h1>
                <p className="mt-2 text-gray-500">
                    Manage users and control access levels across DevFlow.
                </p></div>
            </div>

            {error && (
                <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
                    {error}
                </div>
            )}

            {success && (
                <div className="rounded-lg border border-green-500/20 bg-green-500/10 px-4 py-3 text-sm text-green-400">
                    {success}
                </div>
            )}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
                <RoleSummary
                    label="Administrators"
                    value={totalAdmins}
                    description="Full system access"
                    icon="◈"
                />
                <RoleSummary
                    label="Project Managers"
                    value={totalManagers}
                    description="Project management access"
                    icon="▥"
                />
                <RoleSummary
                    label="Developers"
                    value={totalDevelopers}
                    description="Engineering access"
                    icon="⌘"
                />
                <RoleSummary
                    label="Testers"
                    value={totalTesters}
                    description="Testing access"
                    icon="✓"
                />
                <RoleSummary
                    label="Viewers"
                    value={totalViewers}
                    description="Read-only access"
                    icon="◉"
                />
            </div>

            <div className="dfx-team-directory rounded-xl border border-gray-800 bg-gray-900">
                <div className="border-b border-gray-800 p-6">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-white">
                                Users
                            </h2>
                            <p className="mt-1 text-sm text-gray-500">
                                {users.length} registered users
                            </p>
                        </div>

                        <div className="flex flex-col gap-3 sm:flex-row">
                            <input
                                type="text"
                                placeholder="Search users..."
                                value={search}
                                onChange={e => setSearch(e.target.value)}
                                className="rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none transition placeholder:text-gray-600 focus:border-blue-500"
                            />

                            <select
                                value={roleFilter}
                                onChange={e => setRoleFilter(e.target.value)}
                                className="rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none focus:border-blue-500"
                            >
                                <option value="ALL">All roles</option>
                                {[...new Set(users.map(user => user.role).filter(Boolean))].sort().map(role => <option key={role} value={role}>{roleName(role, globalRoles)}</option>)}
                            </select>
                        </div>
                    </div>
                </div>

                <RecordTable
                    rows={filteredUsers}
                    showSearch={false}
                    getSearchText={user => `${user.name || ""} ${user.email || ""} ${user.role || ""}`}
                    emptyLabel="No users match the selected filters."
                    columns={[
                        { key: "name", label: "Team member", sortValue: user => user.name, render: user => <div className="flex min-w-48 items-center gap-3"><Avatar name={user.name} src={user.avatarUrl} size={38} /><div><p className="font-medium text-white">{user.name || "Unnamed user"}</p><p className="text-xs text-gray-500">ID #{user.id}</p></div></div> },
                        { key: "email", label: "Email", sortValue: user => user.email, render: user => user.email || "—" },
                        { key: "role", label: "Role", sortValue: user => roleName(user.role, globalRoles), render: user => <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-medium ${roleStyles[user.role] || roleStyles.VIEWER}`}>{roleName(user.role, globalRoles)}</span> }
                    ]}
                    actionsForRow={user => canManageRoles && currentUser?.id !== user.id
                        ? [{ label: "Change role", onSelect: () => handleOpenRoleChange(user) }]
                        : []}
                />
            </div>

            {changingUser && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm">
                    <div className="w-full max-w-md rounded-2xl border border-gray-700 bg-gray-900 shadow-2xl">
                        <div className="border-b border-gray-800 p-6">
                            <h2 className="text-xl font-semibold text-white">
                                Change User Role
                            </h2>
                            <p className="mt-2 text-sm text-gray-500">
                                Change the access level for{" "}
                                <span className="text-gray-300">
                                    {changingUser.name}
                                </span>
                                .
                            </p>
                        </div>

                        <div className="p-6">
                            <label className="mb-2 block text-sm font-medium text-gray-300">
                                Role
                            </label>

                            <select
                                value={selectedRole}
                                onChange={e =>
                                    setSelectedRole(e.target.value)
                                }
                                className="w-full rounded-lg border border-gray-700 bg-gray-950 px-4 py-3 text-sm text-white outline-none focus:border-blue-500"
                            >
                                {globalRoles.map(role => <option key={role.id} value={role.code}>{role.name}</option>)}
                            </select>

                            <div className="mt-4 rounded-lg bg-gray-950 p-4">
                                <p className="text-xs uppercase tracking-wider text-gray-600">
                                    Current Role
                                </p>
                                <p className="mt-1 text-sm text-gray-300">
                                    {roleName(changingUser.role, globalRoles)}
                                </p>

                                <p className="mt-3 text-xs uppercase tracking-wider text-gray-600">
                                    New Role
                                </p>
                                <p className="mt-1 text-sm text-blue-400">
                                    {roleName(selectedRole, globalRoles)}
                                </p>
                            </div>
                        </div>

                        <div className="flex justify-end gap-3 border-t border-gray-800 p-6">
                            <button
                                onClick={() => setChangingUser(null)}
                                className="rounded-lg px-4 py-2.5 text-sm text-gray-400 transition hover:bg-gray-800 hover:text-white"
                            >
                                Cancel
                            </button>

                            <button
                                    onClick={handleRoleChange}
                                    disabled={savingRole}
                                className="rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-500"
                            >
                                    {savingRole ? "Updating…" : "Update Role"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

function roleName(code, roles = []) {
    return roles.find(role => role.code === code)?.name || ({ ADMIN: "Administrator", PROJECT_MANAGER: "Project Manager", DEVELOPER: "Developer", TESTER: "Tester", VIEWER: "Viewer" }[code]) || code || "Unknown role";
}

function RoleSummary({ label, value, description, icon }) {
    return (
        <div className="rounded-xl border border-gray-800 bg-gray-900 p-5">
            <div className="flex items-center justify-between">
                <div>
                    <p className="text-sm text-gray-500">{label}</p>
                    <p className="mt-2 text-3xl font-bold text-white">
                        {value}
                    </p>
                </div>

                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gray-800 text-xl">
                    {icon}
                </div>
            </div>

            <p className="mt-3 text-xs text-gray-600">{description}</p>
        </div>
    );
}

export default Team;
