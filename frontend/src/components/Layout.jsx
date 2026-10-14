import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import NotificationBell from "./NotificationBell";
import Avatar from "./Avatar";

const navigation = {
    ADMIN: [
        { name: "Dashboard", path: "/dashboard", icon: "▦" },
        { name: "Users", path: "/team", icon: "♟" },
        { name: "Projects", path: "/projects", icon: "▣" },
        { name: "Issues", path: "/issues", icon: "◇" },
        { name: "Sprints", path: "/sprints", icon: "◷" },
        { name: "Testing", path: "/testing", icon: "✓" },
        { name: "Reports", path: "/reports", icon: "▤" },
        { name: "Comments", path: "/comments", icon: "◌" },
        { name: "Audit Logs", path: "/audit-logs", icon: "◈" },
        { name: "Notifications", path: "/notifications", icon: "♢" },
        { name: "Settings", path: "/settings", icon: "⚙" }
    ],
    PROJECT_MANAGER: [
        { name: "Dashboard", path: "/dashboard", icon: "▦" },
        { name: "Projects", path: "/projects", icon: "▣" },
        { name: "Issues", path: "/issues", icon: "◇" },
        { name: "Sprints", path: "/sprints", icon: "◷" },
        { name: "Testing", path: "/testing", icon: "✓" },
        { name: "Reports", path: "/reports", icon: "▤" },
        { name: "Team", path: "/team", icon: "♟" },
        { name: "Notifications", path: "/notifications", icon: "♢" },
        { name: "Settings", path: "/settings", icon: "⚙" }
    ],
    DEVELOPER: [
        { name: "Dashboard", path: "/dashboard", icon: "▦" },
        { name: "My Issues", path: "/issues", icon: "◇" },
        { name: "My Projects", path: "/projects", icon: "▣" },
        { name: "My Sprint", path: "/sprints", icon: "◷" },
        { name: "Code Review", path: "/code-reviews", icon: "⌕" },
        { name: "Comments", path: "/comments", icon: "◌" },
        { name: "Reports", path: "/reports", icon: "▤" },
        { name: "Notifications", path: "/notifications", icon: "♢" },
        { name: "Settings", path: "/settings", icon: "⚙" }
    ],
    TESTER: [
        { name: "Dashboard", path: "/dashboard", icon: "▦" },
        { name: "Testing Queue", path: "/testing", icon: "✓" },
        { name: "My Tests", path: "/my-tests", icon: "◷" },
        { name: "Reports", path: "/reports", icon: "▤" },
        { name: "Notifications", path: "/notifications", icon: "♢" },
        { name: "Settings", path: "/settings", icon: "⚙" }
    ],
    VIEWER: [
        { name: "Dashboard", path: "/dashboard", icon: "▦" },
        { name: "Projects", path: "/projects", icon: "▣" },
        { name: "Issues", path: "/issues", icon: "◇" },
        { name: "Sprints", path: "/sprints", icon: "◷" },
        { name: "Reports", path: "/reports", icon: "▤" },
        { name: "Activity", path: "/activity", icon: "◈" },
        { name: "Notifications", path: "/notifications", icon: "♢" },
        { name: "Settings", path: "/settings", icon: "⚙" }
    ]
};

function Layout() {
    const navigate = useNavigate();
    const profileRef = useRef(null);
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [profileOpen, setProfileOpen] = useState(false);
    const [preferences, setPreferences] = useState(() => {
        try {
            return JSON.parse(localStorage.getItem("devflow.preferences") || "{}");
        } catch {
            return {};
        }
    });

    useEffect(() => {
        const syncPreferences = () => {
            try {
                setPreferences(JSON.parse(localStorage.getItem("devflow.preferences") || "{}"));
            } catch {
                setPreferences({});
            }
        };
        window.addEventListener("devflow-preferences-change", syncPreferences);
        return () => window.removeEventListener("devflow-preferences-change", syncPreferences);
    }, []);

    useEffect(() => {
        const token = localStorage.getItem("token");

        if (!token) {
            navigate("/login");
            return;
        }

        fetch("http://localhost:8080/api/users/me", {
            headers: {
                Authorization: `Bearer ${token}`
            }
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error("Unauthorized");
                }
                return response.json();
            })
            .then(data => {
                setUser(data);
                setLoading(false);
            })
            .catch(() => {
                localStorage.removeItem("token");
                navigate("/login");
            });
    }, [navigate]);

    useEffect(() => {
        const handleClickOutside = event => {
            if (
                profileRef.current &&
                !profileRef.current.contains(event.target)
            ) {
                setProfileOpen(false);
            }
        };

        document.addEventListener("mousedown", handleClickOutside);

        return () => {
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, []);

    const handleLogout = () => {
        localStorage.removeItem("token");
        navigate("/login");
    };

    if (loading) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-gray-950 text-white">
                <div className="text-center">
                    <div className="mb-3 text-2xl font-bold">DevFlow</div>
                    <p className="text-sm text-gray-500">
                        Loading workspace...
                    </p>
                </div>
            </div>
        );
    }

    const role = user?.role || "VIEWER";
    const links = navigation[role] || navigation.VIEWER;

    const roleLabel =
        role === "ADMIN"
            ? "Administrator"
            : role === "PROJECT_MANAGER"
                ? "Project Manager"
                : role === "DEVELOPER"
                    ? "Developer"
                    : role === "TESTER"
                        ? "Tester"
                    : "Viewer";

    const roleDescription =
        role === "ADMIN"
            ? "System Administration"
            : role === "PROJECT_MANAGER"
                ? "Project Management"
                : role === "DEVELOPER"
                    ? "Engineering Workspace"
                    : role === "TESTER"
                        ? "Testing Workspace"
                    : "Read-Only Workspace";

    return (
        <div className="flex min-h-screen bg-gray-950 text-white">
            <aside className="fixed left-0 top-0 flex h-screen w-64 flex-col border-r border-gray-800 bg-gray-900">
                <div className="border-b border-gray-800 px-6 py-5">
                    <h1 className="text-2xl font-bold text-white">
                        DevFlow
                    </h1>
                    <p className="mt-1 text-xs text-gray-500">
                        Engineering Management
                    </p>
                </div>

                <div className="border-b border-gray-800 px-5 py-4">
                    <div className="flex items-center gap-3">
                        <Avatar name={user?.name} src={user?.avatarUrl} size={40} />

                        <div className="min-w-0">
                            <p className="truncate text-sm font-medium text-white">
                                {user?.name || "User"}
                            </p>

                            <p className="truncate text-xs text-gray-500">
                                {roleLabel}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="px-5 pt-5">
                    <p className="px-2 text-[10px] font-semibold uppercase tracking-widest text-gray-600">
                        {roleDescription}
                    </p>
                </div>

                <nav className="min-h-0 flex-1 space-y-1 overflow-y-auto px-4 py-4">
                    {links.map(link => (
                        <NavLink
                            key={link.path}
                            to={link.path}
                            className={({ isActive }) =>
                                `group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
                                    isActive
                                        ? "bg-blue-600 text-white shadow-lg shadow-blue-900/20"
                                        : "text-gray-400 hover:bg-gray-800 hover:text-white"
                                }`
                            }
                        >
                            <span className="flex w-5 justify-center text-base">
                                {link.icon}
                            </span>

                            <span>{link.name}</span>
                        </NavLink>
                    ))}
                </nav>

                <div className="border-t border-gray-800 p-4">
                    <button
                        onClick={handleLogout}
                        className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-red-400 transition hover:bg-red-500/10 hover:text-red-300"
                    >
                        <span className="w-5 text-center">↪</span>
                        <span>Logout</span>
                    </button>
                </div>
            </aside>

            <div className="ml-64 flex min-h-screen flex-1 flex-col">
                <header className="sticky top-0 z-40 flex h-16 items-center justify-between border-b border-gray-800 bg-gray-950/95 px-8 backdrop-blur">
                    <div>
                        <p className="text-sm text-gray-400">
                            {role === "ADMIN"
                                ? "System Administration"
                                : role === "PROJECT_MANAGER"
                                    ? "Project Management"
                                    : role === "DEVELOPER"
                                        ? "Engineering Workspace"
                                    : role === "TESTER"
                                        ? "Testing Workspace"
                                        : "Workspace Overview"}
                        </p>

                        <h2 className="text-lg font-semibold text-white">
                            Welcome back, {user?.name || "User"}
                        </h2>
                    </div>

                    <div className="flex items-center gap-5">
                            {preferences.showNotificationBell !== false && <NotificationBell />}

                        <div className="relative" ref={profileRef}>
                            <button
                                onClick={() =>
                                    setProfileOpen(!profileOpen)
                                }
                                className="flex items-center gap-3 rounded-lg px-2 py-1.5 transition hover:bg-gray-800"
                            >
                                <div className="text-right">
                                    <p className="text-sm font-medium text-white">
                                        {user?.name || "User"}
                                    </p>

                                    <p className="text-xs text-gray-500">
                                        {roleLabel}
                                    </p>
                                </div>

                                <Avatar name={user?.name} src={user?.avatarUrl} size={40} />

                                <svg
                                    className={`h-4 w-4 text-gray-500 transition-transform ${
                                        profileOpen
                                            ? "rotate-180"
                                            : ""
                                    }`}
                                    fill="none"
                                    stroke="currentColor"
                                    viewBox="0 0 24 24"
                                >
                                    <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        strokeWidth={2}
                                        d="M19 9l-7 7-7-7"
                                    />
                                </svg>
                            </button>

                            {profileOpen && (
                                <div className="absolute right-0 top-full mt-3 w-64 overflow-hidden rounded-xl border border-gray-700 bg-gray-900 shadow-2xl">
                                    <div className="border-b border-gray-700 px-4 py-4">
                                        <div className="flex items-center gap-3">
                                            <Avatar name={user?.name} src={user?.avatarUrl} size={44} />

                                            <div className="min-w-0">
                                                <p className="truncate font-medium text-white">
                                                    {user?.name || "User"}
                                                </p>

                                                <p className="truncate text-xs text-gray-500">
                                                    {user?.email || ""}
                                                </p>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="p-2">
                                        <button
                                            onClick={() => {
                                                setProfileOpen(false);
                                                navigate("/account");
                                            }}
                                            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-gray-300 transition hover:bg-gray-800 hover:text-white"
                                        >
                                            <span>👤</span>
                                            <span>My Account</span>
                                        </button>

                                        <button
                                            onClick={() => {
                                                setProfileOpen(false);
                                                navigate("/settings");
                                            }}
                                            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-gray-300 transition hover:bg-gray-800 hover:text-white"
                                        >
                                            <span>⚙️</span>
                                            <span>Settings</span>
                                        </button>

                                        <div className="my-2 border-t border-gray-800" />

                                        <button
                                            onClick={handleLogout}
                                            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-red-400 transition hover:bg-red-500/10 hover:text-red-300"
                                        >
                                            <span>🚪</span>
                                            <span>Logout</span>
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </header>

                <main className={`flex-1 ${preferences.compactMode ? "p-5" : "p-8"}`}>
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

export default Layout;
