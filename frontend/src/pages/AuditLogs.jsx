import { apiUrl } from "../services/apiConfig";
import { useEffect, useMemo, useState } from "react";

const API = apiUrl("/api");
const ALLOWED_ROLES = ["ADMIN", "PROJECT_MANAGER"];
const ROLE_LABELS = { ADMIN: "Administrator", PROJECT_MANAGER: "Project Manager", DEVELOPER: "Developer", VIEWER: "Viewer" };
const list = value => Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : [];
const dateValue = value => value && !Number.isNaN(new Date(value).getTime()) ? new Date(value) : null;
const dateLabel = value => dateValue(value)?.toLocaleString() || "Time unavailable";
const formatAction = action => String(action || "Activity").replaceAll("ISSUE", "TASK").replaceAll("issue", "task").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, char => char.toUpperCase());
const formatTaskDetails = value => String(value || "").replace(/\bissues?\b/gi, word => `${word[0] === word[0].toUpperCase() ? "T" : "t"}ask${word.toLowerCase().endsWith("s") ? "s" : ""}`);

function AuditLogs() {
    const [user, setUser] = useState(null);
    const [issues, setIssues] = useState([]);
    const [projects, setProjects] = useState([]);
    const [issueId, setIssueId] = useState("");
    const [logs, setLogs] = useState([]);
    const [logsForIssueId, setLogsForIssueId] = useState("");
    const [issueSearch, setIssueSearch] = useState("");
    const [projectFilter, setProjectFilter] = useState("ALL");
    const [actionFilter, setActionFilter] = useState("ALL");
    const [loading, setLoading] = useState(true);
    const [logsLoading, setLogsLoading] = useState(true);
    const [error, setError] = useState("");
    const [logsError, setLogsError] = useState("");

    useEffect(() => {
        let active = true;
        const headers = { Authorization: `Bearer ${localStorage.getItem("token")}` };
        (async () => {
            const currentResponse = await fetch(`${API}/users/me`, { headers });
            if (!currentResponse.ok) throw new Error("Could not verify your account.");
            const current = await currentResponse.json();
            if (!active) return;
            setUser(current);
            if (!ALLOWED_ROLES.includes(current.role)) return;
            const [issueResponse, projectResponse] = await Promise.all([fetch(`${API}/issues`, { headers }), fetch(`${API}/projects`, { headers })]);
            if (!issueResponse.ok || !projectResponse.ok) throw new Error("Could not load tasks and projects.");
            const [issueData, projectData] = await Promise.all([issueResponse.json(), projectResponse.json()]);
            if (!active) return;
            const issueList = list(issueData);
            setIssues(issueList);
            setProjects(list(projectData));
            if (issueList[0]) setIssueId(String(issueList[0].id));
        })().catch(reason => { if (active) setError(reason.message || "Could not load audit log data."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const visibleIssues = useMemo(() => issues.filter(issue => {
        const projectId = String(issue.project?.id ?? "");
        return (projectFilter === "ALL" || projectId === projectFilter) && (issue.title || "").toLowerCase().includes(issueSearch.toLowerCase());
    }), [issues, projectFilter, issueSearch]);
    const selectedIssue = visibleIssues.find(issue => String(issue.id) === issueId) || visibleIssues[0] || null;
    const selectedIssueId = selectedIssue?.id;

    useEffect(() => {
        if (selectedIssueId == null || !ALLOWED_ROLES.includes(user?.role)) return undefined;
        let active = true;
        fetch(`${API}/audit-logs/issue/${selectedIssueId}`, { headers: { Authorization: `Bearer ${localStorage.getItem("token")}` } })
            .then(async response => {
                if (!response.ok) throw new Error("Could not load history for this task.");
                return list(await response.json());
            })
            .then(data => { if (active) { setLogs(data); setLogsForIssueId(String(selectedIssueId)); setLogsLoading(false); } })
            .catch(reason => { if (active) { setLogsError(reason.message); setLogsLoading(false); } })
            .finally(() => { if (active) setLogsLoading(false); });
        return () => { active = false; };
    }, [selectedIssueId, user?.role]);

    const sortedLogs = useMemo(() => [...logs].sort((a, b) => (dateValue(b.createdAt)?.getTime() || 0) - (dateValue(a.createdAt)?.getTime() || 0)), [logs]);
    const actions = [...new Set(logs.map(log => log.action).filter(Boolean))];
    const filteredLogs = sortedLogs.filter(log => actionFilter === "ALL" || log.action === actionFilter);
    const inputClass = "rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none focus:border-blue-500";

    if (loading) return <div className="flex min-h-[50vh] items-center justify-center text-sm text-gray-400">Loading audit history…</div>;
    if (!user || !ALLOWED_ROLES.includes(user.role)) return <div className="dfx-access-state mx-auto mt-10 max-w-xl rounded-2xl border border-gray-800 bg-gray-900 p-8 text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-500/10 text-xl text-amber-300">!</div><h1 className="mt-4 text-2xl font-semibold text-white">Access denied</h1><p className="mt-2 text-sm text-gray-400">Audit Logs are available to Administrators and Project Managers. Your current role is {ROLE_LABELS[user?.role] || user?.role || "unknown"}. The backend remains responsible for access control.</p>{error && <p className="mt-3 text-xs text-red-400">{error}</p>}</div>;

    return <div className="dfx-screen dfx-screen--audit mx-auto max-w-6xl space-y-7">
        <header className="dfx-screen-header"><p className="text-sm font-medium text-blue-400">WORKSPACE HISTORY</p><h1 className="mt-2 text-3xl font-semibold text-white">Audit Logs</h1><p className="mt-2 text-sm text-gray-400">Track important activity and changes across DevFlow.</p></header>
        {error && <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">{error}</div>}

        <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 sm:p-6"><div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px_200px]"><input className={inputClass} value={issueSearch} onChange={event => setIssueSearch(event.target.value)} placeholder="Search tasks…" aria-label="Search tasks" /><select className={inputClass} value={projectFilter} onChange={event => setProjectFilter(event.target.value)} aria-label="Filter by project"><option value="ALL">All projects</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name || `Project ${project.id}`}</option>)}</select><select className={inputClass} value={actionFilter} onChange={event => setActionFilter(event.target.value)} aria-label="Filter by action"><option value="ALL">All actions</option>{actions.map(action => <option key={action} value={action}>{formatAction(action)}</option>)}</select></div><label htmlFor="audit-task" className="mt-4 block text-xs font-medium uppercase tracking-wider text-gray-500">Task history</label><select id="audit-task" value={selectedIssue ? String(selectedIssue.id) : ""} onChange={event => { setIssueId(event.target.value); setLogsError(""); }} className={`${inputClass} mt-2 w-full`}>{visibleIssues.length === 0 && <option value="">No tasks match your search</option>}{visibleIssues.map(issue => <option key={issue.id} value={issue.id}>{issue.title || `Task #${issue.id}`} · {issue.project?.name || "Project unavailable"}</option>)}</select></section>

        <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 sm:p-7"><div className="mb-6 flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold text-white">Activity timeline</h2><p className="mt-1 text-sm text-gray-500">{selectedIssue ? `Task #${selectedIssue.id} · ${selectedIssue.title || "Untitled task"}` : "Select a task to inspect its history."}</p></div>{selectedIssue && <div className="flex gap-2"><span className="rounded-full bg-gray-800 px-3 py-1 text-xs text-gray-300">{selectedIssue.status || "Unknown status"}</span><span className="rounded-full bg-blue-500/10 px-3 py-1 text-xs text-blue-300">{selectedIssue.project?.name || "No project"}</span></div>}</div>
            {logsError && <div role="alert" className="mb-5 rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-300">{logsError}</div>}
            {!selectedIssue ? <p className="py-10 text-center text-sm text-gray-400">Select a task to view its audit history.</p> : logsLoading || logsForIssueId !== String(selectedIssue.id) ? <p className="py-10 text-center text-sm text-gray-400">Loading task history…</p> : filteredLogs.length === 0 ? <div className="rounded-xl border border-dashed border-gray-700 p-10 text-center"><p className="font-medium text-white">No audit activity found</p><p className="mt-2 text-sm text-gray-500">{logs.length && actionFilter !== "ALL" ? "Try selecting a different action." : "This task has no recorded history yet."}</p></div> : <div className="space-y-0">{filteredLogs.map((log, index) => {
                const actor = log.user || log.actor || log.performedBy || {};
                const details = formatTaskDetails(typeof log.details === "string" ? log.details : log.details == null ? "" : JSON.stringify(log.details));
                return <article key={log.id ?? `${log.action}-${log.createdAt}-${index}`} className="relative flex gap-4 pb-6 last:pb-0"><div className="flex w-6 shrink-0 flex-col items-center"><span className="mt-1.5 h-3 w-3 rounded-full border-2 border-blue-400 bg-blue-500/20" />{index < filteredLogs.length - 1 && <span className="mt-2 w-px flex-1 bg-gray-700" />}</div><div className="min-w-0 flex-1 rounded-xl border border-gray-800 bg-gray-950/60 p-4 sm:p-5"><div className="flex flex-wrap items-start justify-between gap-3"><div><span className="inline-flex rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-300">{formatAction(log.action)}</span><p className="mt-3 text-sm leading-relaxed text-gray-300">{details || "No additional details were recorded."}</p></div><time className="shrink-0 text-xs text-gray-500">{dateLabel(log.createdAt || log.timestamp)}</time></div><div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 border-t border-gray-800 pt-3 text-xs text-gray-500"><span>Task #{selectedIssue?.id}: {selectedIssue?.title || "Untitled task"}</span>{actor.name && <span>By {actor.name}</span>}{actor.role && <span>{ROLE_LABELS[actor.role] || actor.role}</span>}</div></div></article>;
            })}</div>}
        </section>
    </div>;
}

export default AuditLogs;
