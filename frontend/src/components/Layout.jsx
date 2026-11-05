import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import NotificationBell from "./NotificationBell";
import Avatar from "./Avatar";
import { apiUrl } from "../services/issueApi";
import { effectivePermissionSet } from "../utils/effectivePermissions";

const navigation = [
    { name: "Dashboard", path: "/dashboard", icon: "▦", any: ["DASHBOARD_READ", "DASHBOARD_READ_ALL"] },
    { name: "Users", path: "/team", icon: "♟", any: ["USER_READ"] },
    { name: "Projects", path: "/projects", icon: "▣", any: ["PROJECT_READ", "PROJECT_READ_ALL"] },
    { name: "Private Teams", path: "/private-teams", icon: "♙", any: ["TEAM_READ", "TEAM_READ_ALL", "TEAM_CREATE"] },
    { name: "Tasks", path: "/issues", icon: "◇", any: ["ISSUE_READ"] },
    { name: "Sprints", path: "/sprints", icon: "◷", any: ["PROJECT_READ", "PROJECT_READ_ALL"] },
    { name: "Testing", path: "/testing", icon: "✓", any: ["TESTING_QUEUE_READ", "TESTING_MY_READ"] },
    { name: "Pending Approvals", path: "/dashboard#pending-approvals", icon: "⌛", any: ["ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER"], approvals: true },
    { name: "My Tests", path: "/my-tests", icon: "◷", any: ["TESTING_MY_READ"] },
    { name: "Code Review", path: "/code-reviews", icon: "⌕", any: ["ISSUE_READ"] },
    { name: "Comments", path: "/comments", icon: "◌", any: ["COMMENT_READ"] },
    { name: "Reports", path: "/reports", icon: "▤", any: ["REPORT_READ"] },
    { name: "Activity", path: "/activity", icon: "◈", any: ["PROJECT_READ", "PROJECT_READ_ALL"], all: ["ISSUE_READ"] },
    { name: "Audit Logs", path: "/audit-logs", icon: "◈", any: ["AUDIT_READ", "AUDIT_READ_ALL"] },
    { name: "Notifications", path: "/notifications", icon: "♢", any: [] },
    { name: "Automations", path: "/automations", icon: "↻", any: ["AUTOMATION_MANAGE"] },
    { name: "Settings", path: "/settings", icon: "⚙", any: [] },
    { name: "Admin Panel", path: "/admin", icon: "▦", any: ["ROLE_MANAGE"], protected: true }
];

const roleLabels = { ADMIN: "Administrator", PROJECT_MANAGER: "Project Manager", DEVELOPER: "Developer", TESTER: "Tester", VIEWER: "Viewer" };
const roleDescriptions = { ADMIN: "System administration", PROJECT_MANAGER: "Project management", DEVELOPER: "Engineering workspace", TESTER: "Testing workspace", VIEWER: "Read-only workspace" };

function Layout() {
    const navigate = useNavigate();
    const location = useLocation();
    const profileRef = useRef(null);
    const [user, setUser] = useState(null);
    const [effectivePermissions, setEffectivePermissions] = useState(null);
    const [permissionsUnavailable, setPermissionsUnavailable] = useState(false);
    const [menuConfiguration, setMenuConfiguration] = useState(null);
    const [websiteConfiguration, setWebsiteConfiguration] = useState({});
    const [loading, setLoading] = useState(true);
    const [approvalCount, setApprovalCount] = useState(0);
    const [profileOpen, setProfileOpen] = useState(false);
    const [mobileOpen, setMobileOpen] = useState(false);
    const [preferences, setPreferences] = useState(() => {
        try { return JSON.parse(localStorage.getItem("devflow.preferences") || "{}"); }
        catch { return {}; }
    });

    useEffect(() => {
        const syncPreferences = () => {
            try { setPreferences(JSON.parse(localStorage.getItem("devflow.preferences") || "{}")); }
            catch { setPreferences({}); }
        };
        window.addEventListener("devflow-preferences-change", syncPreferences);
        return () => window.removeEventListener("devflow-preferences-change", syncPreferences);
    }, []);

    useEffect(() => {
        const token = localStorage.getItem("token");
        if (!token) { navigate("/login", { replace: true }); return; }
        let active = true;
        const headers = { Authorization: `Bearer ${token}` };
        Promise.all([
            fetch(apiUrl("/api/users/me"), { headers }),
            fetch(apiUrl("/api/users/me/permissions"), { headers }),
            fetch(apiUrl("/api/config/menu"), { headers }).catch(() => null),
            fetch(apiUrl("/api/config/website"), { headers }).catch(() => null)
        ])
            .then(async ([profileResponse, permissionsResponse, menuResponse, websiteResponse]) => {
                if (!profileResponse.ok) throw new Error("Unauthorized");
                const profile = await profileResponse.json();
                const permissionData = permissionsResponse.ok ? await permissionsResponse.json() : null;
                const menuData = menuResponse?.ok ? await menuResponse.json() : null;
                const websiteData = websiteResponse?.ok ? await websiteResponse.json() : [];
                if (active) {
                    setUser(profile);
                    setEffectivePermissions(permissionData);
                    setMenuConfiguration(Array.isArray(menuData) ? menuData : null);
                    setWebsiteConfiguration(Object.fromEntries((Array.isArray(websiteData) ? websiteData : []).map(item => [item.key, item.value])));
                    setPermissionsUnavailable(!permissionData);
                    setLoading(false);
                }
            })
            .catch(() => { if (active) { localStorage.removeItem("token"); navigate("/login", { replace: true }); } });
        return () => { active = false; };
    }, [navigate]);

    useEffect(() => {
        const onKeyDown = event => { if (event.key === "Escape") { setMobileOpen(false); setProfileOpen(false); } };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, []);

    useEffect(() => {
        const token = localStorage.getItem("token");
        const mayApprove = (effectivePermissions?.global || []).some(value => ["ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER"].includes(value))
            || [...(effectivePermissions?.projects || []), ...(effectivePermissions?.teams || [])].some(scope => (scope.permissions || []).some(value => ["ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER"].includes(value)));
        if (!token || !mayApprove) return undefined;
        let active = true;
        const load = () => fetch(apiUrl("/api/approvals/pending"), { headers: { Authorization: `Bearer ${token}` } })
            .then(response => response.ok ? response.json() : null)
            .then(data => { if (active && data) setApprovalCount(Number(data.teamLeadPendingCount || 0) + Number(data.projectManagerPendingCount || 0)); })
            .catch(() => {});
        load();
        const interval = window.setInterval(load, 60000);
        window.addEventListener("devflow-approvals-changed", load);
        window.addEventListener("devflow-notification-received", load);
        return () => { active = false; window.clearInterval(interval); window.removeEventListener("devflow-approvals-changed", load); window.removeEventListener("devflow-notification-received", load); };
    }, [effectivePermissions]);
    useEffect(() => {
        const onOutside = event => { if (profileRef.current && !profileRef.current.contains(event.target)) setProfileOpen(false); };
        document.addEventListener("mousedown", onOutside);
        return () => document.removeEventListener("mousedown", onOutside);
    }, []);

    const logout = () => { localStorage.removeItem("token"); navigate("/login", { replace: true }); };
    if (loading) return <div className="flex min-h-screen items-center justify-center bg-gray-950 text-white"><div className="text-center"><div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-xl bg-blue-500 font-bold text-white shadow-lg shadow-blue-950/50">D</div><p className="text-sm text-gray-400">Loading workspace…</p></div></div>;

    const role = user?.role || "VIEWER";
    const permissionSet = effectivePermissionSet(effectivePermissions);
    const configuredMenus = new Map((menuConfiguration || []).map(entry => [entry.routeKey, entry]));
    const routeKeys = { "/dashboard":"dashboard", "/team":"users", "/projects":"projects", "/private-teams":"private-teams", "/issues":"tasks", "/sprints":"sprints", "/testing":"testing", "/my-tests":"my-tests", "/code-reviews":"code-reviews", "/comments":"comments", "/reports":"reports", "/activity":"activity", "/audit-logs":"audit-logs", "/notifications":"notifications", "/automations":"automations", "/settings":"settings" };
    const links = navigation.map((link, index) => ({ ...link, configuredOrder: configuredMenus.get(routeKeys[link.path])?.sortOrder ?? index * 10, configuredLabel: configuredMenus.get(routeKeys[link.path])?.displayLabel }))
        .filter(link => {
            if (link.protected && !effectivePermissions?.recoveryCapable) return false;
            if (permissionsUnavailable) return !link.protected && !link.any.length && !configuredMenus.get(routeKeys[link.path])?.requiredPermission;
            if (link.any.length && !link.any.some(permission => permissionSet.has(permission))) return false;
            if ((link.all || []).some(permission => !permissionSet.has(permission))) return false;
            const configuredPermission = configuredMenus.get(routeKeys[link.path])?.requiredPermission;
            if (configuredPermission && !(effectivePermissions?.global || []).includes(configuredPermission)) return false;
            if (!link.protected && !link.approvals && menuConfiguration && !configuredMenus.get(routeKeys[link.path])?.enabled) return false;
            return true;
        })
        .map(link => ({ ...link, name: link.configuredLabel || link.name }))
        .sort((a,b) => a.configuredOrder - b.configuredOrder);
    const roleLabel = roleLabels[role] || role.replaceAll("_", " ");
    const sidebar = <>
        <div className="flex h-[72px] items-center gap-3 border-b border-gray-800/80 px-5">
            <div className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 text-base font-bold text-white shadow-md shadow-blue-950/40">D</div>
            <div><p className="text-[15px] font-semibold tracking-tight text-white">{websiteConfiguration["workspace.name"] || "DevFlow"}</p><p className="text-[10px] font-medium uppercase tracking-[0.16em] text-gray-500">{websiteConfiguration["workspace.tagline"] || "Engineering OS"}</p></div>
            <button type="button" aria-label="Close navigation" onClick={() => setMobileOpen(false)} className="ml-auto rounded-lg p-2 text-gray-400 hover:bg-gray-800 lg:hidden">×</button>
        </div>
        <div className="border-b border-gray-800/70 px-4 py-4"><div className="flex items-center gap-3 rounded-xl border border-gray-800/80 bg-gray-950/65 p-3"><Avatar name={user?.name} src={user?.avatarUrl} size={38} /><div className="min-w-0"><p className="truncate text-sm font-medium text-gray-100">{user?.name || "User"}</p><p className="mt-0.5 truncate text-xs text-gray-500">{roleLabel}</p></div><span className="ml-auto h-2 w-2 rounded-full bg-emerald-400 ring-4 ring-emerald-500/10" title="Signed in" /></div></div>
        <div className="px-5 pb-2 pt-5"><p className="px-2 text-[10px] font-semibold uppercase tracking-[0.17em] text-gray-600">{roleDescriptions[role] || "Workspace"}</p></div>
        <nav aria-label="Main navigation" className="min-h-0 flex-1 space-y-1 overflow-y-auto px-3 py-2">
            {links.map(link => <NavLink key={link.path} to={link.path} onClick={() => { setMobileOpen(false); setProfileOpen(false); }} className={({ isActive }) => `group flex items-center gap-3 rounded-lg border px-3 py-2.5 text-[13px] font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-400 ${isActive ? "border-blue-500/20 bg-blue-500/10 text-blue-300" : "border-transparent text-gray-400 hover:bg-gray-800/70 hover:text-gray-100"}`}><span aria-hidden="true" className="grid w-5 place-items-center text-base opacity-80">{link.icon}</span><span>{link.name}</span>{link.path === "/testing" && role === "TESTER" && <span className="ml-auto rounded-md bg-gray-800 px-1.5 py-0.5 text-[10px] text-gray-400">Queue</span>}{link.approvals && approvalCount > 0 && <span className="ml-auto min-w-5 rounded-full bg-violet-500/20 px-1.5 py-0.5 text-center text-[10px] text-violet-200">{approvalCount}</span>}</NavLink>)}
        </nav>
        <div className="border-t border-gray-800/80 p-3"><button onClick={logout} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-medium text-gray-400 transition hover:bg-red-500/10 hover:text-red-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-red-400"><span aria-hidden="true" className="grid w-5 place-items-center">↪</span>Sign out</button></div>
    </>;

    return <div className="dfx-app-shell min-h-screen bg-gray-950 text-gray-100">
        {mobileOpen && <button type="button" aria-label="Close navigation overlay" onClick={() => setMobileOpen(false)} className="fixed inset-0 z-40 bg-black/60 backdrop-blur-[2px] lg:hidden" />}
        <aside className={`fixed inset-y-0 left-0 z-50 flex dfx-sidebar w-[276px] flex-col border-r border-gray-800/80 bg-gray-900 transition-transform duration-200 lg:translate-x-0 ${mobileOpen ? "translate-x-0 shadow-2xl" : "-translate-x-full"}`}>{sidebar}</aside>
        <div className="min-h-screen lg:pl-[276px]">
            <header className="dfx-topbar sticky top-0 z-30 flex h-[72px] items-center justify-between border-b border-gray-800/80 bg-gray-950/90 px-4 backdrop-blur-xl sm:px-6 lg:px-8">
                <div className="flex min-w-0 items-center gap-3"><button type="button" aria-label="Open navigation" aria-expanded={mobileOpen} onClick={() => setMobileOpen(true)} className="grid h-9 w-9 shrink-0 place-items-center rounded-lg border border-gray-800 bg-gray-900 text-gray-300 hover:border-gray-700 hover:bg-gray-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-400 lg:hidden"><span aria-hidden="true">☰</span></button><div className="min-w-0"><p className="hidden text-[11px] font-medium uppercase tracking-[0.14em] text-gray-500 sm:block">{roleDescriptions[role]}</p><p className="truncate text-sm font-semibold text-gray-100 sm:mt-0.5 sm:text-base">{pageTitle(location.pathname)}</p></div></div>
                <div className="flex items-center gap-2 sm:gap-3">{preferences.showNotificationBell !== false && <NotificationBell />}<div className="relative" ref={profileRef}><button type="button" aria-label="Open account menu" aria-expanded={profileOpen} onClick={() => setProfileOpen(value => !value)} className="flex items-center gap-2 rounded-xl border border-transparent p-1.5 transition hover:border-gray-800 hover:bg-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-400 sm:gap-3 sm:pl-2"><span className="hidden max-w-36 text-right sm:block"><span className="block truncate text-xs font-medium text-gray-200">{user?.name || "User"}</span><span className="mt-0.5 block truncate text-[11px] text-gray-500">{roleLabel}</span></span><Avatar name={user?.name} src={user?.avatarUrl} size={36} /><span aria-hidden="true" className={`hidden text-[10px] text-gray-500 transition-transform sm:block ${profileOpen ? "rotate-180" : ""}`}>⌄</span></button>
                    {profileOpen && <div className="absolute right-0 top-full mt-2 w-64 overflow-hidden rounded-xl border border-gray-800 bg-gray-900 p-1.5 shadow-2xl shadow-black/40"><div className="border-b border-gray-800 px-3 py-3"><p className="truncate text-sm font-medium text-gray-100">{user?.name || "User"}</p><p className="mt-0.5 truncate text-xs text-gray-500">{user?.email || ""}</p></div><button onClick={() => { setProfileOpen(false); navigate("/account"); }} className="mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-gray-300 hover:bg-gray-800"><span className="w-5 text-center">◎</span>My account</button><button onClick={() => { setProfileOpen(false); navigate("/settings"); }} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-gray-300 hover:bg-gray-800"><span className="w-5 text-center">⚙</span>Settings</button><button onClick={logout} className="mt-1 flex w-full items-center gap-3 rounded-lg border-t border-gray-800 px-3 py-2.5 text-left text-sm text-red-300 hover:bg-red-500/10"><span className="w-5 text-center">↪</span>Sign out</button></div>}
                </div></div>
            </header>
            <main className={`dfx-main-content min-h-[calc(100vh-72px)] ${preferences.compactMode ? "p-4 sm:p-5" : "p-4 sm:p-6 lg:p-8"}`}><div className="mx-auto w-full max-w-[1600px]">{permissionsUnavailable && <div role="status" className="mb-5 rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-200">Permission-aware navigation is temporarily unavailable. The server still enforces access to every page.</div>}<Outlet context={{ effectivePermissions, permissionsUnavailable }} /></div></main>
        </div>
    </div>;
}

function pageTitle(path) {
    const labels = { "/dashboard": "Dashboard", "/projects": "Projects", "/private-teams": "Private teams", "/issues": "Tasks", "/team": "Team", "/sprints": "Sprints", "/account": "My account", "/settings": "Settings", "/admin": "Admin Panel", "/comments": "Comments", "/notifications": "Notifications", "/team-workload": "Team workload", "/audit-logs": "Audit logs", "/reports": "Reports", "/code-reviews": "Code reviews", "/activity": "Activity", "/testing": "Testing queue", "/testing-queue": "Testing queue", "/my-tests": "My tests", "/automations": "Automations" };
    if (path.startsWith("/testing/")) return "Test workspace";
    return labels[path] || "Workspace";
}

export default Layout;
