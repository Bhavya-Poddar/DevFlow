import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Avatar from "../components/Avatar";

const asList = data => Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : [];

function Dashboard() {
    const navigate = useNavigate();
    const [user, setUser] = useState(null);
    const [users, setUsers] = useState([]);
    const [projects, setProjects] = useState([]);
    const [issues, setIssues] = useState([]);
    const [sprints, setSprints] = useState([]);
    const [auditLogs, setAuditLogs] = useState([]);
    const [testerQueue, setTesterQueue] = useState([]);
    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        const token = localStorage.getItem("token");

        if (!token) {
            navigate("/login");
            return;
        }

        const headers = {
            Authorization: `Bearer ${token}`
        };

        const loadDashboard = async () => {
            try {
                const [userRes, usersRes, projectRes, issueRes, sprintRes, summaryRes] =
                    await Promise.all([
                        fetch("http://localhost:8080/api/users/me", { headers }),
                        fetch("http://localhost:8080/api/users", { headers }),
                        fetch("http://localhost:8080/api/projects", { headers }),
                        fetch("http://localhost:8080/api/issues", { headers }),
                        fetch("http://localhost:8080/api/sprints", { headers }),
                        fetch("http://localhost:8080/api/dashboard/summary", { headers })
                    ]);

                if (!userRes.ok) {
                    throw new Error("Unauthorized");
                }

                if (!summaryRes.ok) throw new Error("Could not load dashboard analytics.");
                const userData = await userRes.json();
                const summaryData = await summaryRes.json();
                const userList = usersRes.ok ? asList(await usersRes.json()) : [];
                const projectList = projectRes.ok ? asList(await projectRes.json()) : [];
                const issueList = issueRes.ok ? asList(await issueRes.json()) : [];
                const sprintList = sprintRes.ok ? asList(await sprintRes.json()) : [];

                setUser(userData);
                setUsers(userList);
                setProjects(projectList);
                setIssues(issueList);
                setSprints(sprintList);
                setSummary(summaryData);

                if (userData.role === "TESTER") {
                    setTesterQueue(issueList.filter(issue =>
                        ["READY_FOR_TESTING", "TESTING"].includes(issue.status) &&
                        String(issue.tester?.id ?? issue.testerId ?? "") === String(userData.id)
                    ));
                }

                if (userData.role === "ADMIN" && issueList.length > 0) {
                    const logResults = await Promise.all(
                        issueList.slice(0, 10).map(issue =>
                            fetch(
                                `http://localhost:8080/api/audit-logs/issue/${issue.id}`,
                                { headers }
                            )
                                .then(response =>
                                    response.ok ? response.json() : []
                                )
                                .catch(() => [])
                        )
                    );

                    const logs = logResults
                        .flat()
                        .sort(
                            (a, b) =>
                                new Date(b.createdAt) -
                                new Date(a.createdAt)
                        )
                        .slice(0, 8);

                    setAuditLogs(logs);
                }
            } catch (error) {
                console.error("Dashboard loading error:", error);
                setError(error.message || "Could not load dashboard data.");
            } finally {
                setLoading(false);
            }
        };

        loadDashboard();
    }, [navigate]);

    if (loading) {
        return (
            <div className="flex min-h-[60vh] items-center justify-center">
                <div className="text-center">
                    <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-gray-700 border-t-blue-500" />
                    <p className="mt-4 text-sm text-gray-500">
                        Loading dashboard...
                    </p>
                </div>
            </div>
        );
    }

    if (error) {
        return <main role="alert" className="mx-auto max-w-3xl rounded-xl border border-red-500/30 bg-red-500/10 p-6 text-red-300">{error}</main>;
    }

    let roleDashboard;
    if (user?.role === "ADMIN") {
        roleDashboard = <AdminDashboard navigate={navigate} users={users} projects={projects} issues={issues} sprints={sprints} auditLogs={auditLogs} />;
    } else if (user?.role === "PROJECT_MANAGER") {
        roleDashboard = <ManagerDashboard user={user} projects={projects} issues={issues} sprints={sprints} />;
    } else if (user?.role === "DEVELOPER") {
        roleDashboard = <DeveloperDashboard user={user} issues={issues} sprints={sprints} />;
    } else if (user?.role === "TESTER") {
        roleDashboard = <TesterDashboard issues={testerQueue} navigate={navigate} />;
    } else {
        roleDashboard = <ViewerDashboard projects={projects} issues={issues} sprints={sprints} />;
    }

    return <div className="mx-auto max-w-7xl space-y-8"><DashboardAnalytics summary={summary} />{roleDashboard}</div>;
}

function DashboardAnalytics({ summary }) {
    const statuses = summary?.statusDistribution || [];
    const projects = summary?.projectProgress || [];
    const workload = summary?.teamWorkload || [];
    const maxStatus = Math.max(1, ...statuses.map(item => item.count));
    const maxWorkload = Math.max(1, ...workload.map(item => item.assignedIssues));
    const cards = [
        ["Projects", summary?.totalProjects ?? 0, `${summary?.totalSprints ?? 0} sprints`],
        ["Tracked issues", summary?.totalIssues ?? 0, `${summary?.activeIssues ?? 0} active`],
        ["Completed", summary?.completedIssues ?? 0, summary?.totalIssues ? `${Math.round(summary.completedIssues * 100 / summary.totalIssues)}% completion` : "No issues yet"],
        ["In testing", summary?.testingIssues ?? 0, `${summary?.activeSprints ?? 0} active sprints`],
    ];
    return <section className="space-y-5" aria-label="Engineering analytics">
        <div><p className="text-xs font-semibold uppercase tracking-[0.18em] text-blue-400">Engineering overview</p><h1 className="mt-2 text-2xl font-semibold tracking-tight text-white">Delivery at a glance</h1><p className="mt-1 text-sm text-gray-400">Live metrics from your projects, issues, sprints, and team assignments.</p></div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{cards.map(([title, value, hint]) => <article key={title} className="rounded-2xl border border-gray-800 bg-gradient-to-br from-gray-900 to-gray-950 p-5 shadow-lg shadow-black/10"><p className="text-sm text-gray-400">{title}</p><p className="mt-3 text-3xl font-semibold tracking-tight text-white">{value}</p><p className="mt-2 text-xs text-gray-500">{hint}</p></article>)}</div>
        <div className="grid gap-5 xl:grid-cols-3">
            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5"><div className="mb-5"><h2 className="font-semibold text-white">Issue status</h2><p className="mt-1 text-xs text-gray-500">Current distribution across the workspace</p></div>{statuses.length === 0 ? <p className="rounded-xl border border-dashed border-gray-800 p-5 text-sm text-gray-500">No issue status data yet.</p> : <div className="space-y-4">{statuses.map(item => <div key={item.status}><div className="mb-1.5 flex justify-between gap-3 text-xs"><span className="text-gray-300">{String(item.status || "Unknown").replaceAll("_", " ")}</span><span className="text-gray-500">{item.count}</span></div><div className="h-2 overflow-hidden rounded-full bg-gray-800"><div className="h-full rounded-full bg-gradient-to-r from-blue-500 to-cyan-400" style={{ width: `${Math.max(3, item.count * 100 / maxStatus)}%` }} /></div></div>)}</div>}</section>
            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 xl:col-span-2"><div className="mb-5"><h2 className="font-semibold text-white">Project progress</h2><p className="mt-1 text-xs text-gray-500">Resolved issues as a share of each project’s tracked work</p></div>{projects.length === 0 ? <p className="rounded-xl border border-dashed border-gray-800 p-5 text-sm text-gray-500">No projects to report yet.</p> : <div className="space-y-4">{projects.slice(0, 6).map(project => <div key={project.projectId}><div className="mb-1.5 flex items-center justify-between gap-4"><span className="truncate text-sm text-gray-200">{project.projectName}</span><span className="shrink-0 text-xs text-gray-500">{project.completedIssues}/{project.totalIssues} · {project.completionPercent}%</span></div><div className="h-2 overflow-hidden rounded-full bg-gray-800"><div className="h-full rounded-full bg-gradient-to-r from-violet-500 to-blue-400" style={{ width: `${project.completionPercent}%` }} /></div></div>)}</div>}</section>
            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 xl:col-span-3"><div className="mb-5"><h2 className="font-semibold text-white">Team workload</h2><p className="mt-1 text-xs text-gray-500">Issues assigned to developers and testers</p></div>{workload.length === 0 ? <p className="rounded-xl border border-dashed border-gray-800 p-5 text-sm text-gray-500">No active assignments yet.</p> : <div className="grid gap-4 md:grid-cols-2">{workload.slice(0, 8).map(person => <div key={`${person.role}-${person.userId}`} className="rounded-xl border border-gray-800 bg-gray-950/70 p-4"><div className="mb-3 flex items-center justify-between gap-4"><div className="flex min-w-0 items-center gap-3"><Avatar name={person.name} src={person.avatarUrl} size={36} /><div className="min-w-0"><p className="truncate text-sm font-medium text-gray-200">{person.name}</p><p className="mt-1 text-[11px] uppercase tracking-wide text-gray-500">{person.role.replaceAll("_", " ")}</p></div></div><span className="text-sm font-semibold text-blue-300">{person.assignedIssues}</span></div><div className="h-1.5 overflow-hidden rounded-full bg-gray-800"><div className="h-full rounded-full bg-blue-500" style={{ width: `${Math.max(person.assignedIssues ? 5 : 0, person.assignedIssues * 100 / maxWorkload)}%` }} /></div></div>)}</div>}</section>
        </div>
    </section>;
}
function TesterDashboard({ issues, navigate }) {
    const ready=issues.filter(i=>i.status==="READY_FOR_TESTING");
    const active=issues.filter(i=>i.status==="TESTING");
    const passed=issues.filter(i=>(i.testResult||i.result)==="PASSED").length;
    const failed=issues.filter(i=>(i.testResult||i.result)==="FAILED").length;
    return <main className="mx-auto max-w-7xl space-y-7"><header><p className="text-sm font-semibold uppercase tracking-wide text-blue-400">TESTER WORKSPACE</p><h1 className="mt-2 text-3xl font-bold text-white">Testing Workspace</h1><p className="mt-2 text-gray-400">Your assigned testing work and recent outcomes.</p></header><section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><DashboardStat title="Ready for Testing" value={ready.length} description="Assigned and waiting" icon="◷"/><DashboardStat title="Currently Testing" value={active.length} description="Active test executions" icon="✓"/><DashboardStat title="Passed" value={passed} description="Passed items in your queue" icon="✓"/><DashboardStat title="Failed" value={failed} description="Failed items in your queue" icon="!"/></section><section className="rounded-xl border border-gray-800 bg-gray-900 p-5"><div className="mb-4 flex items-center justify-between"><div><h2 className="text-lg font-semibold text-white">My Testing Queue</h2><p className="mt-1 text-sm text-gray-500">Issues assigned to your tester account.</p></div><button onClick={()=>navigate("/my-tests")} className="rounded-lg border border-gray-700 px-3 py-2 text-sm text-gray-300 hover:bg-gray-800">View all</button></div>{issues.length===0?<EmptyState text="No testing issues are assigned to you."/>:<div className="space-y-2">{issues.slice(0,6).map(issue=><button key={issue.id} onClick={()=>navigate(`/testing/${issue.id}`)} className="flex w-full flex-wrap items-center justify-between gap-3 rounded-lg border border-gray-800 bg-gray-950 p-4 text-left hover:border-blue-500/50"><span><span className="block font-medium text-white">{issue.title||`Issue #${issue.id}`}</span><span className="mt-1 block text-xs text-gray-500">{issue.project?.name||"Project unavailable"} · {issue.priority||"—"}</span></span><span className="rounded-full bg-gray-800 px-3 py-1 text-xs text-gray-300">{String(issue.status||"UNKNOWN").replaceAll("_"," ")}</span></button>)}</div>}</section></main>;
}

function AdminDashboard({
    navigate,
    users,
    projects,
    issues,
    sprints,
    auditLogs
}) {
    const admins = users.filter(user => user.role === "ADMIN").length;
    const managers = users.filter(
        user => user.role === "PROJECT_MANAGER"
    ).length;
    const developers = users.filter(
        user => user.role === "DEVELOPER"
    ).length;
    const viewers = users.filter(user => user.role === "VIEWER").length;

    const openIssues = issues.filter(
        issue =>
            issue.status !== "RESOLVED" &&
            issue.status !== "CLOSED"
    ).length;

    const completedIssues = issues.filter(
        issue =>
            issue.status === "RESOLVED" ||
            issue.status === "CLOSED"
    ).length;

    const inProgressIssues = issues.filter(
        issue => issue.status === "IN_PROGRESS"
    ).length;

    const reviewIssues = issues.filter(
        issue => issue.status === "CODE_REVIEW"
    ).length;

    const testingIssues = issues.filter(
        issue => issue.status === "TESTING"
    ).length;

    const activeSprints = sprints.filter(
        sprint => sprint.status === "ACTIVE"
    ).length;

    const completedProjects = projects.filter(
        project => project.status === "COMPLETED"
    ).length;

    const activeProjects = projects.filter(
        project => project.status === "ACTIVE"
    ).length;

    const issueStatusData = [
        {
            label: "Open",
            value: issues.filter(issue => issue.status === "OPEN").length,
            style: "bg-blue-500"
        },
        {
            label: "In Progress",
            value: inProgressIssues,
            style: "bg-yellow-500"
        },
        {
            label: "Code Review",
            value: reviewIssues,
            style: "bg-purple-500"
        },
        {
            label: "Testing",
            value: testingIssues,
            style: "bg-orange-500"
        },
        {
            label: "Resolved",
            value: issues.filter(issue => issue.status === "RESOLVED").length,
            style: "bg-green-500"
        },
        {
            label: "Closed",
            value: issues.filter(issue => issue.status === "CLOSED").length,
            style: "bg-gray-500"
        }
    ];

    const maxIssueCount = Math.max(
        ...issueStatusData.map(item => item.value),
        1
    );

    return (
        <div className="space-y-8">
            <div>
                <p className="text-sm font-medium text-blue-400">
                    ADMINISTRATION
                </p>
                <h1 className="mt-1 text-3xl font-bold text-white">
                    System Overview
                </h1>
                <p className="mt-2 text-gray-500">
                    Monitor and manage the entire DevFlow engineering workspace.
                </p>
            </div>

            <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
                <DashboardStat
                    title="Total Users"
                    value={users.length}
                    description="Registered accounts"
                    icon="👥"
                />
                <DashboardStat
                    title="Projects"
                    value={projects.length}
                    description={`${activeProjects} active projects`}
                    icon="📁"
                />
                <DashboardStat
                    title="Open Issues"
                    value={openIssues}
                    description={`${completedIssues} completed`}
                    icon="🐛"
                />
                <DashboardStat
                    title="Active Sprints"
                    value={activeSprints}
                    description={`${sprints.length} total sprints`}
                    icon="⚡"
                />
            </div>

            <div className="grid gap-6 xl:grid-cols-3">
                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6 xl:col-span-2">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-white">
                                User Distribution
                            </h2>
                            <p className="mt-1 text-sm text-gray-500">
                                Current access levels
                            </p>
                        </div>

                        <button
                            onClick={() => navigate("/team")}
                            className="rounded-lg border border-gray-700 px-3 py-2 text-xs text-gray-300 transition hover:border-blue-500 hover:bg-blue-500/10 hover:text-blue-400"
                        >
                            Manage Users →
                        </button>
                    </div>

                    <div className="mt-6 grid gap-4 sm:grid-cols-2">
                        <RoleCard
                            title="Administrators"
                            value={admins}
                            icon="🛡️"
                            description="Full system access"
                            className="border-red-500/20 bg-red-500/5"
                            textClass="text-red-400"
                        />

                        <RoleCard
                            title="Project Managers"
                            value={managers}
                            icon="📊"
                            description="Project management"
                            className="border-purple-500/20 bg-purple-500/5"
                            textClass="text-purple-400"
                        />

                        <RoleCard
                            title="Developers"
                            value={developers}
                            icon="💻"
                            description="Engineering access"
                            className="border-green-500/20 bg-green-500/5"
                            textClass="text-green-400"
                        />

                        <RoleCard
                            title="Viewers"
                            value={viewers}
                            icon="👁️"
                            description="Read-only access"
                            className="border-gray-700 bg-gray-950"
                            textClass="text-gray-400"
                        />
                    </div>
                </div>

                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-white">
                                Issue Overview
                            </h2>
                            <p className="mt-1 text-sm text-gray-500">
                                Current workflow status
                            </p>
                        </div>

                        <button
                            onClick={() => navigate("/issues")}
                            className="text-xs text-blue-400 hover:text-blue-300"
                        >
                            View all →
                        </button>
                    </div>

                    <div className="mt-6 space-y-4">
                        {issueStatusData.map(item => (
                            <div key={item.label}>
                                <div className="mb-2 flex items-center justify-between text-xs">
                                    <span className="text-gray-400">
                                        {item.label}
                                    </span>
                                    <span className="font-medium text-white">
                                        {item.value}
                                    </span>
                                </div>

                                <div className="h-2 overflow-hidden rounded-full bg-gray-800">
                                    <div
                                        className={`h-full rounded-full ${item.style}`}
                                        style={{
                                            width: `${Math.max(
                                                (item.value /
                                                    maxIssueCount) *
                                                    100,
                                                item.value > 0 ? 8 : 0
                                            )}%`
                                        }}
                                    />
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-white">
                                Project Health
                            </h2>
                            <p className="mt-1 text-sm text-gray-500">
                                Current project status
                            </p>
                        </div>

                        <button
                            onClick={() => navigate("/projects")}
                            className="text-xs text-blue-400 hover:text-blue-300"
                        >
                            View projects →
                        </button>
                    </div>

                    <div className="mt-6 space-y-4">
                        {projects.slice(0, 5).map(project => (
                            <div
                                key={project.id}
                                className="flex items-center justify-between rounded-lg border border-gray-800 bg-gray-950 p-4"
                            >
                                <div className="min-w-0">
                                    <p className="truncate font-medium text-white">
                                        {project.name}
                                    </p>
                                    <p className="mt-1 truncate text-xs text-gray-500">
                                        {project.description ||
                                            "No description"}
                                    </p>
                                </div>

                                <span
                                    className={`ml-4 shrink-0 rounded-full px-3 py-1 text-xs ${
                                        project.status === "COMPLETED"
                                            ? "bg-green-500/10 text-green-400"
                                            : "bg-blue-500/10 text-blue-400"
                                    }`}
                                >
                                    {project.status || "ACTIVE"}
                                </span>
                            </div>
                        ))}

                        {projects.length === 0 && (
                            <EmptyState text="No projects available." />
                        )}

                        {projects.length > 0 && (
                            <div className="grid grid-cols-2 gap-3 pt-2">
                                <MiniMetric
                                    label="Active"
                                    value={activeProjects}
                                />
                                <MiniMetric
                                    label="Completed"
                                    value={completedProjects}
                                />
                            </div>
                        )}
                    </div>
                </div>

                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-white">
                                Sprint Status
                            </h2>
                            <p className="mt-1 text-sm text-gray-500">
                                Current sprint activity
                            </p>
                        </div>

                        <button
                            onClick={() => navigate("/sprints")}
                            className="text-xs text-blue-400 hover:text-blue-300"
                        >
                            View sprints →
                        </button>
                    </div>

                    <div className="mt-6 space-y-4">
                        {sprints.slice(0, 5).map(sprint => (
                            <div
                                key={sprint.id}
                                className="flex items-center justify-between border-b border-gray-800 pb-4"
                            >
                                <div>
                                    <p className="font-medium text-white">
                                        {sprint.name}
                                    </p>
                                    <p className="mt-1 text-xs text-gray-500">
                                        {sprint.goal || "No sprint goal"}
                                    </p>
                                </div>

                                <span
                                    className={`rounded-full px-3 py-1 text-xs ${
                                        sprint.status === "ACTIVE"
                                            ? "bg-green-500/10 text-green-400"
                                            : "bg-gray-800 text-gray-400"
                                    }`}
                                >
                                    {sprint.status}
                                </span>
                            </div>
                        ))}

                        {sprints.length === 0 && (
                            <EmptyState text="No sprints available." />
                        )}
                    </div>
                </div>
            </div>

            <div className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-lg font-semibold text-white">
                            Recent System Activity
                        </h2>
                        <p className="mt-1 text-sm text-gray-500">
                            Latest recorded engineering activity
                        </p>
                    </div>

                    <button
                        onClick={() => navigate("/issues")}
                        className="text-xs text-blue-400 hover:text-blue-300"
                    >
                        View activity →
                    </button>
                </div>

                <div className="mt-6">
                    {auditLogs.length === 0 ? (
                        <EmptyState text="No recent activity available." />
                    ) : (
                        <div className="space-y-1">
                            {auditLogs.map((log, index) => (
                                <div
                                    key={`${log.id}-${index}`}
                                    className="flex items-center gap-4 rounded-lg p-4 transition hover:bg-gray-800/50"
                                >
                                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-500/10 text-sm text-blue-400">
                                        {getActivityIcon(log.action)}
                                    </div>

                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm text-gray-300">
                                            {log.details || log.action}
                                        </p>
                                        <p className="mt-1 text-xs text-gray-600">
                                            {formatDate(log.createdAt)}
                                        </p>
                                    </div>

                                    <span className="hidden rounded-full bg-gray-800 px-3 py-1 text-xs text-gray-500 sm:block">
                                        {formatAction(log.action)}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            <div className="grid gap-5 md:grid-cols-3">
                <QuickAction
                    icon="👥"
                    title="Manage Users"
                    description="Change roles and access"
                    onClick={() => navigate("/team")}
                />

                <QuickAction
                    icon="📁"
                    title="Manage Projects"
                    description="View and manage projects"
                    onClick={() => navigate("/projects")}
                />

                <QuickAction
                    icon="🐛"
                    title="Manage Issues"
                    description={`${issues.length} total issues`}
                    onClick={() => navigate("/issues")}
                />
            </div>
        </div>
    );
}

function DashboardStat({ title, value, description, icon }) {
    return (
        <div className="rounded-xl border border-gray-800 bg-gray-900 p-5 transition hover:border-gray-700">
            <div className="flex items-start justify-between">
                <div>
                    <p className="text-sm text-gray-500">{title}</p>
                    <p className="mt-2 text-3xl font-bold text-white">
                        {value}
                    </p>
                </div>

                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gray-800 text-lg">
                    {icon}
                </div>
            </div>

            <p className="mt-3 text-xs text-gray-600">{description}</p>
        </div>
    );
}

function RoleCard({
    title,
    value,
    icon,
    description,
    className,
    textClass
}) {
    return (
        <div className={`rounded-xl border p-4 ${className}`}>
            <div className="flex items-center justify-between">
                <div>
                    <p className="text-sm text-gray-400">{title}</p>
                    <p className={`mt-1 text-2xl font-bold ${textClass}`}>
                        {value}
                    </p>
                </div>

                <span className="text-xl">{icon}</span>
            </div>

            <p className="mt-3 text-xs text-gray-600">{description}</p>
        </div>
    );
}

function MiniMetric({ label, value }) {
    return (
        <div className="rounded-lg bg-gray-950 p-3">
            <p className="text-xs text-gray-600">{label}</p>
            <p className="mt-1 text-lg font-semibold text-white">{value}</p>
        </div>
    );
}

function QuickAction({ icon, title, description, onClick }) {
    return (
        <button
            onClick={onClick}
            className="group rounded-xl border border-gray-800 bg-gray-900 p-5 text-left transition hover:border-blue-500/40 hover:bg-gray-800/70"
        >
            <div className="flex items-center gap-4">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-500/10 text-xl transition group-hover:bg-blue-500/20">
                    {icon}
                </div>

                <div>
                    <p className="font-medium text-white">{title}</p>
                    <p className="mt-1 text-xs text-gray-500">
                        {description}
                    </p>
                </div>
            </div>
        </button>
    );
}

function EmptyState({ text }) {
    return (
        <div className="rounded-lg border border-dashed border-gray-800 p-6 text-center">
            <p className="text-sm text-gray-500">{text}</p>
        </div>
    );
}

function getActivityIcon(action) {
    if (action?.includes("COMMENT")) return "💬";
    if (action?.includes("STATUS")) return "🔄";
    if (action?.includes("ASSIGNED")) return "👤";
    if (action?.includes("SPRINT")) return "⚡";
    if (action?.includes("DELETED")) return "🗑️";
    if (action?.includes("CREATED")) return "✨";
    if (action?.includes("ROLE")) return "🛡️";
    return "•";
}

function formatAction(action) {
    if (!action) return "ACTIVITY";

    return action
        .replaceAll("_", " ")
        .toLowerCase()
        .replace(/\b\w/g, letter => letter.toUpperCase());
}

function formatDate(date) {
    if (!date) return "Unknown time";

    const value = new Date(date);

    if (Number.isNaN(value.getTime())) {
        return "Unknown time";
    }

    return value.toLocaleString([], {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    });
}

function ManagerDashboard({ projects, issues, sprints }) {
    const activeIssues = issues.filter(
        issue => issue.status === "IN_PROGRESS"
    ).length;

    const completedIssues = issues.filter(
        issue =>
            issue.status === "RESOLVED" ||
            issue.status === "CLOSED"
    ).length;

    const activeSprints = sprints.filter(
        sprint => sprint.status === "ACTIVE"
    ).length;

    return (
        <div className="space-y-8">
            <div>
                <p className="text-sm text-purple-400">
                    PROJECT MANAGEMENT
                </p>
                <h1 className="mt-1 text-3xl font-bold text-white">
                    Manager Dashboard
                </h1>
                <p className="mt-2 text-gray-500">
                    Monitor projects, teams, issues and sprint progress.
                </p>
            </div>

            <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
                <DashboardStat
                    title="My Projects"
                    value={projects.length}
                    description="Projects you manage"
                    icon="📁"
                />
                <DashboardStat
                    title="Total Issues"
                    value={issues.length}
                    description="Across your projects"
                    icon="🐛"
                />
                <DashboardStat
                    title="In Progress"
                    value={activeIssues}
                    description="Issues currently active"
                    icon="🔄"
                />
                <DashboardStat
                    title="Completed"
                    value={completedIssues}
                    description="Resolved or closed"
                    icon="✅"
                />
            </div>

            <div className="grid gap-6 lg:grid-cols-3">
                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6 lg:col-span-2">
                    <h2 className="text-lg font-semibold text-white">
                        Projects
                    </h2>

                    <div className="mt-5 space-y-3">
                        {projects.slice(0, 6).map(project => (
                            <div
                                key={project.id}
                                className="flex items-center justify-between rounded-lg border border-gray-800 bg-gray-950 p-4"
                            >
                                <div>
                                    <p className="font-medium text-white">
                                        {project.name}
                                    </p>
                                    <p className="mt-1 text-xs text-gray-500">
                                        {project.description ||
                                            "No description"}
                                    </p>
                                </div>

                                <span className="rounded-full bg-purple-500/10 px-3 py-1 text-xs text-purple-400">
                                    {project.status || "ACTIVE"}
                                </span>
                            </div>
                        ))}

                        {projects.length === 0 && (
                            <EmptyState text="No projects available." />
                        )}
                    </div>
                </div>

                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                    <h2 className="text-lg font-semibold text-white">
                        Sprint Status
                    </h2>

                    <div className="mt-5">
                        <div className="rounded-xl bg-purple-500/10 p-5">
                            <p className="text-sm text-purple-400">
                                ACTIVE SPRINTS
                            </p>
                            <p className="mt-2 text-4xl font-bold text-white">
                                {activeSprints}
                            </p>
                        </div>

                        <div className="mt-4 space-y-3">
                            {sprints.slice(0, 4).map(sprint => (
                                <div
                                    key={sprint.id}
                                    className="border-b border-gray-800 pb-3"
                                >
                                    <p className="text-sm font-medium text-white">
                                        {sprint.name}
                                    </p>
                                    <p className="text-xs text-gray-500">
                                        {sprint.status}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function DeveloperDashboard({
    user,
    issues,
    sprints
}) {
    const myIssues = issues.filter(
        issue => issue.assignedUser?.email === user?.email
    );

    const inProgress = myIssues.filter(
        issue => issue.status === "IN_PROGRESS"
    ).length;

    const completed = myIssues.filter(
        issue =>
            issue.status === "RESOLVED" ||
            issue.status === "CLOSED"
    ).length;

    const codeReview = myIssues.filter(
        issue => issue.status === "CODE_REVIEW"
    ).length;

    const activeSprint = sprints.find(
        sprint => sprint.status === "ACTIVE"
    );

    return (
        <div className="space-y-8">
            <div>
                <p className="text-sm text-green-400">
                    DEVELOPER WORKSPACE
                </p>
                <h1 className="mt-1 text-3xl font-bold text-white">
                    My Development Dashboard
                </h1>
                <p className="mt-2 text-gray-500">
                    Focus on your assigned engineering work.
                </p>
            </div>

            <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
                <DashboardStat
                    title="My Issues"
                    value={myIssues.length}
                    description="Assigned to you"
                    icon="💻"
                />
                <DashboardStat
                    title="In Progress"
                    value={inProgress}
                    description="Currently working on"
                    icon="🔄"
                />
                <DashboardStat
                    title="Code Review"
                    value={codeReview}
                    description="Waiting for review"
                    icon="🔍"
                />
                <DashboardStat
                    title="Completed"
                    value={completed}
                    description="Successfully completed"
                    icon="✅"
                />
            </div>

            <div className="grid gap-6 lg:grid-cols-3">
                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6 lg:col-span-2">
                    <h2 className="text-lg font-semibold text-white">
                        My Issues
                    </h2>

                    <div className="mt-5 space-y-3">
                        {myIssues.slice(0, 8).map(issue => (
                            <div
                                key={issue.id}
                                className="rounded-lg border border-gray-800 bg-gray-950 p-4"
                            >
                                <div className="flex items-start justify-between gap-4">
                                    <div>
                                        <p className="font-medium text-white">
                                            {issue.title}
                                        </p>
                                        <p className="mt-1 text-xs text-gray-500">
                                            {issue.description ||
                                                "No description"}
                                        </p>
                                    </div>

                                    <span className="rounded-full bg-green-500/10 px-3 py-1 text-xs text-green-400">
                                        {issue.status}
                                    </span>
                                </div>

                                <p className="mt-3 text-xs text-gray-500">
                                    Priority:{" "}
                                    <span className="text-gray-300">
                                        {issue.priority}
                                    </span>
                                </p>
                            </div>
                        ))}

                        {myIssues.length === 0 && (
                            <EmptyState text="No issues are currently assigned to you." />
                        )}
                    </div>
                </div>

                <div className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                    <p className="text-sm text-green-400">
                        CURRENT SPRINT
                    </p>

                    {activeSprint ? (
                        <>
                            <h2 className="mt-3 text-xl font-semibold text-white">
                                {activeSprint.name}
                            </h2>

                            <p className="mt-2 text-sm text-gray-500">
                                {activeSprint.goal ||
                                    "No sprint goal specified."}
                            </p>

                            <div className="mt-6 rounded-lg bg-gray-950 p-4">
                                <p className="text-xs text-gray-500">
                                    MY SPRINT ISSUES
                                </p>
                                <p className="mt-1 text-2xl font-bold text-white">
                                    {
                                        myIssues.filter(
                                            issue =>
                                                issue.sprint?.id ===
                                                activeSprint.id
                                        ).length
                                    }
                                </p>
                            </div>
                        </>
                    ) : (
                        <EmptyState text="No active sprint." />
                    )}
                </div>
            </div>
        </div>
    );
}

function ViewerDashboard({
    projects,
    issues,
    sprints
}) {
    return (
        <div className="space-y-8">
            <div>
                <p className="text-sm text-gray-400">
                    READ-ONLY WORKSPACE
                </p>
                <h1 className="mt-1 text-3xl font-bold text-white">
                    Project Overview
                </h1>
                <p className="mt-2 text-gray-500">
                    View the current state of engineering projects.
                </p>
            </div>

            <div className="grid gap-5 md:grid-cols-3">
                <DashboardStat
                    title="Projects"
                    value={projects.length}
                    description="Available projects"
                    icon="📁"
                />
                <DashboardStat
                    title="Issues"
                    value={issues.length}
                    description="Tracked issues"
                    icon="🐛"
                />
                <DashboardStat
                    title="Sprints"
                    value={sprints.length}
                    description="Project sprints"
                    icon="⚡"
                />
            </div>

            <div className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                <h2 className="text-lg font-semibold text-white">
                    Projects
                </h2>

                <div className="mt-5 grid gap-4 md:grid-cols-2">
                    {projects.map(project => (
                        <div
                            key={project.id}
                            className="rounded-lg border border-gray-800 bg-gray-950 p-5"
                        >
                            <h3 className="font-medium text-white">
                                {project.name}
                            </h3>

                            <p className="mt-2 text-sm text-gray-500">
                                {project.description ||
                                    "No description"}
                            </p>

                            <p className="mt-4 text-xs text-gray-500">
                                Status:{" "}
                                <span className="text-gray-300">
                                    {project.status}
                                </span>
                            </p>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

export default Dashboard;
