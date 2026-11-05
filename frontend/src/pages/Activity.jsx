import { apiUrl } from "../services/apiConfig";
import { useEffect, useMemo, useState } from "react";

const API = apiUrl("/api");
const list = value => Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : [];
const validDate = value => value && !Number.isNaN(new Date(value).getTime()) ? new Date(value) : null;
const formatDate = value => validDate(value)?.toLocaleString() || null;
const titleCase = value => String(value || "Unknown").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());

function Activity() {
    const [user, setUser] = useState(null);
    const [projects, setProjects] = useState([]);
    const [issues, setIssues] = useState([]);
    const [sprints, setSprints] = useState([]);
    const [filter, setFilter] = useState("ALL");
    const [search, setSearch] = useState("");
    const [myIssuesOnly, setMyIssuesOnly] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        const headers = { Authorization: `Bearer ${localStorage.getItem("token")}` };
        (async () => {
            const userResponse = await fetch(`${API}/users/me`, { headers });
            if (!userResponse.ok) throw new Error("Could not verify your account.");
            const current = await userResponse.json();
            if (!active) return;
            setUser(current);
            const responses = await Promise.all([
                fetch(`${API}/projects`, { headers }),
                fetch(`${API}/issues`, { headers }),
                fetch(`${API}/sprints`, { headers })
            ]);
            if (responses.some(response => !response.ok)) throw new Error("Could not load workspace activity.");
            const [projectData, issueData, sprintData] = await Promise.all(responses.map(response => response.json()));
            if (!active) return;
            setProjects(list(projectData));
            setIssues(list(issueData));
            setSprints(list(sprintData));
        })().catch(reason => { if (active) setError(reason.message || "Could not load activity."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const feed = useMemo(() => {
        const entries = [
            ...issues.map(issue => {
                const assigned = issue.assignedUser || issue.assignee || issue.assignedTo;
                const assignedName = typeof assigned === "object" ? assigned.name || assigned.email : null;
                const assignedId = typeof assigned === "object" ? assigned.id : assigned;
                const assignedEmail = typeof assigned === "object" ? assigned.email : null;
                const assignedUserId = issue.assignedUserId ?? issue.assigneeId ?? issue.assignedToId;
                const assignedToMe = (user?.id != null && String(assignedId ?? assignedUserId ?? "") === String(user.id)) || Boolean(user?.email && assignedEmail?.toLowerCase() === user.email.toLowerCase());
                const description = [`Current status: ${titleCase(issue.status)}`, issue.priority ? `Priority: ${titleCase(issue.priority)}` : null, assignedName ? `Assigned to ${assignedName}` : assigned ? `Assigned user #${assigned}` : "Unassigned"].filter(Boolean).join(" · ");
                return { id: `task-${issue.id}`, category: "ISSUES", kind: "Task", title: issue.title || `Task #${issue.id}`, description, assignedToMe, context: `${issue.project?.name || "Project unavailable"}${issue.id != null ? ` · Task #${issue.id}` : ""}`, time: issue.updatedAt ? `Last updated ${formatDate(issue.updatedAt)}` : issue.createdAt ? `Created ${formatDate(issue.createdAt)}` : null, sortTime: validDate(issue.updatedAt || issue.createdAt)?.getTime() || 0, accent: "blue" };
            }),
            ...projects.map(project => ({
                id: `project-${project.id}`, category: "PROJECTS", kind: "Project", title: project.name || `Project #${project.id}`,
                description: project.description || "No project description provided.", context: `Current status: ${titleCase(project.status)}`,
                time: project.updatedAt ? `Last updated ${formatDate(project.updatedAt)}` : project.createdAt ? `Created ${formatDate(project.createdAt)}` : null,
                sortTime: validDate(project.updatedAt || project.createdAt)?.getTime() || 0, accent: "violet"
            })),
            ...sprints.map(sprint => ({
                id: `sprint-${sprint.id}`, category: "SPRINTS", kind: "Sprint", title: sprint.name || `Sprint #${sprint.id}`,
                description: sprint.goal || "No sprint goal provided.", context: [`Current status: ${titleCase(sprint.status)}`, sprint.project?.name, sprint.startDate ? `Starts ${sprint.startDate}` : null, sprint.endDate ? `Ends ${sprint.endDate}` : null].filter(Boolean).join(" · "),
                time: sprint.updatedAt ? `Last updated ${formatDate(sprint.updatedAt)}` : null,
                sortTime: validDate(sprint.updatedAt)?.getTime() || 0, accent: "emerald"
            }))
        ];
        return entries.sort((a, b) => b.sortTime - a.sortTime);
    }, [issues, projects, sprints, user]);

    const filtered = feed.filter(entry => (filter === "ALL" || entry.category === filter) && (!myIssuesOnly || (user?.role === "DEVELOPER" && entry.category === "ISSUES" && entry.assignedToMe)) && `${entry.kind} ${entry.title} ${entry.description} ${entry.context}`.toLowerCase().includes(search.toLowerCase()));

    if (loading) return <div className="flex min-h-[50vh] items-center justify-center text-sm text-gray-400">Loading workspace activity…</div>;
    const roleDescriptions = {
        ADMIN: "Broad overview of available project, task, and sprint activity.",
        PROJECT_MANAGER: "Project and team activity across the workspace data available to you.",
        DEVELOPER: "Engineering-focused current task activity, assignments, and delivery status.",
        TESTER: "Testing activity, task assignments, and delivery status.",
        VIEWER: "A read-only overview of project, task, and sprint progress."
    };

    return <div className="dfx-screen dfx-screen--activity mx-auto max-w-5xl space-y-7">
        <header className="dfx-screen-header"><p className="text-sm font-medium text-blue-400">{user?.role === "ADMIN" ? "SYSTEM ACTIVITY" : user?.role === "PROJECT_MANAGER" ? "PROJECT & TEAM ACTIVITY" : user?.role === "DEVELOPER" ? "ENGINEERING ACTIVITY" : user?.role === "TESTER" ? "TESTING ACTIVITY" : "READ-ONLY WORKSPACE"}</p><h1 className="mt-2 text-3xl font-semibold text-white">Activity</h1><p className="mt-2 text-sm text-gray-400">{roleDescriptions[user?.role] || "Stay informed about changes and progress across your workspace."}</p><p className="mt-3 text-xs text-gray-500">This overview reflects current data from your workspace. Historical changes appear only when the source record provides a date.</p></header>
        {error && <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">{error}</div>}
        <section className="grid gap-4 sm:grid-cols-3"><Snapshot label={user?.role === "DEVELOPER" ? "Assigned tasks" : "Tasks"} value={user?.role === "DEVELOPER" ? feed.filter(entry => entry.category === "ISSUES" && entry.assignedToMe).length : issues.length} tone="blue" /><Snapshot label="Projects" value={projects.length} tone="violet" /><Snapshot label="Sprints" value={sprints.length} tone="emerald" /></section>
        <section className="overflow-hidden rounded-2xl border border-gray-800 bg-gray-900"><div className="flex flex-col gap-4 border-b border-gray-800 p-5 sm:flex-row sm:items-center sm:justify-between"><div className="flex flex-wrap gap-2" role="group" aria-label="Filter activity">{[["ALL", "All"], ["ISSUES", "Tasks"], ["PROJECTS", "Projects"], ["SPRINTS", "Sprints"]].map(([key, text]) => <button type="button" key={key} onClick={() => setFilter(key)} aria-pressed={filter === key} className={`rounded-lg px-3.5 py-2 text-sm font-medium transition ${filter === key ? "bg-blue-600 text-white" : "text-gray-400 hover:bg-gray-800 hover:text-white"}`}>{text}</button>)}</div><div className="flex flex-col gap-3 sm:flex-row">{user?.role === "DEVELOPER" && <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-gray-700 px-3 py-2 text-sm text-gray-300"><input type="checkbox" checked={myIssuesOnly} onChange={event => setMyIssuesOnly(event.target.checked)} className="accent-blue-500" />My tasks only</label>}<input className="w-full rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none placeholder:text-gray-500 focus:border-blue-500 sm:max-w-xs" value={search} onChange={event => setSearch(event.target.value)} placeholder="Search activity…" aria-label="Search activity" /></div></div>
            {filtered.length === 0 ? <div className="p-12 text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-gray-800 text-lg text-gray-400">◷</div><h2 className="mt-4 font-medium text-white">No activity to show</h2><p className="mt-2 text-sm text-gray-500">{feed.length ? "Try another category or search term." : "There are no project, task, or sprint records available yet."}</p></div> : <div className="divide-y divide-gray-800">{filtered.map(entry => <ActivityCard key={entry.id} entry={entry} />)}</div>}
        </section>
    </div>;
}

function Snapshot({ label, value, tone }) { const styles = { blue: "text-blue-300 bg-blue-500/10", violet: "text-violet-300 bg-violet-500/10", emerald: "text-emerald-300 bg-emerald-500/10" }; return <div className="rounded-2xl border border-gray-800 bg-gray-900 p-5"><div className="flex items-center justify-between"><p className="text-sm text-gray-500">{label}</p><span className={`flex h-9 w-9 items-center justify-center rounded-lg ${styles[tone]}`}>•</span></div><p className="mt-3 text-3xl font-semibold text-white">{value}</p></div>; }
function ActivityCard({ entry }) { const styles = { blue: "bg-blue-500/10 text-blue-300", violet: "bg-violet-500/10 text-violet-300", emerald: "bg-emerald-500/10 text-emerald-300" }; return <article className="flex gap-4 p-5"><div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-xs font-semibold ${styles[entry.accent]}`}>{entry.kind.slice(0, 1)}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-gray-800 px-2.5 py-1 text-[11px] text-gray-300">{entry.kind}</span>{entry.time && <span className="text-xs text-gray-500">{entry.time}</span>}</div><h2 className="mt-2 font-medium text-white">{entry.title}</h2><p className="mt-1 text-sm leading-relaxed text-gray-400">{entry.description}</p><p className="mt-2 text-xs text-gray-500">{entry.context}</p></div></article>; }

export default Activity;
