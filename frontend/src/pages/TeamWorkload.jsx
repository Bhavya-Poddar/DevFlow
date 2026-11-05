import { apiUrl } from "../services/apiConfig";
import { useEffect, useMemo, useState } from "react";
import Avatar from "../components/Avatar";
import { loadDropdownOptions, dropdownLabel } from "../services/dropdownOptions";

const API = apiUrl("/api");
const STATUSES = ["OPEN", "IN_PROGRESS", "CODE_REVIEW", "TESTING", "RESOLVED", "CLOSED"];
const ROLES = { ADMIN: "Administrator", PROJECT_MANAGER: "Project Manager", DEVELOPER: "Developer", TESTER: "Tester", VIEWER: "Viewer" };
const list = value => Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : [];
const who = issue => issue.assignedUser || issue.assignee || issue.user || null;
const high = issue => ["HIGH", "CRITICAL"].includes(String(issue.priority || "").toUpperCase());

function TeamWorkload() {
    const [user, setUser] = useState(null);
    const [members, setMembers] = useState([]);
    const [issues, setIssues] = useState([]);
    const [projects, setProjects] = useState([]);
    const [search, setSearch] = useState("");
    const [roleFilter, setRoleFilter] = useState("ALL");
    const [workloadFilter, setWorkloadFilter] = useState("ALL");
    const [projectFilter, setProjectFilter] = useState("ALL");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [workloadOptions, setWorkloadOptions] = useState([{code:"LOW",displayLabel:"Low"},{code:"MEDIUM",displayLabel:"Medium"},{code:"HIGH",displayLabel:"High"}]);

    useEffect(() => { loadDropdownOptions("WORKLOAD_LEVEL").then(data=>{if(data.length)setWorkloadOptions(data);}).catch(()=>{}); }, []);

    useEffect(() => {
        let active = true;
        const token = localStorage.getItem("token");
        const headers = { Authorization: `Bearer ${token}` };
        (async () => {
            const currentResponse = await fetch(`${API}/users/me`, { headers });
            if (!currentResponse.ok) throw new Error("Could not verify your account.");
            const current = await currentResponse.json();
            if (!active) return;
            setUser(current);
            if (current.role !== "PROJECT_MANAGER") return;
            const responses = await Promise.all([
                fetch(`${API}/users/assignable`, { headers }),
                fetch(`${API}/issues`, { headers }),
                fetch(`${API}/projects`, { headers })
            ]);
            if (responses.some(response => !response.ok)) throw new Error("Could not load team workload data.");
            const [team, issueData, projectData] = await Promise.all(responses.map(response => response.json()));
            if (!active) return;
            setMembers(list(team));
            setIssues(list(issueData));
            setProjects(list(projectData));
        })().catch(reason => { if (active) setError(reason.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const teamData = useMemo(() => members.map(member => {
        const assigned = issues.filter(issue => String(who(issue)?.id ?? "") === String(member.id));
        const statusCounts = Object.fromEntries(STATUSES.map(status => [status, assigned.filter(issue => issue.status === status).length]));
        const level = assigned.length >= 8 ? "HIGH" : assigned.length >= 4 ? "MEDIUM" : "LOW";
        return { member, assigned, statusCounts, level, highPriority: assigned.filter(high).length };
    }), [members, issues]);

    const visibleMembers = teamData.filter(({ member, assigned, level }) => {
        const matchesSearch = `${member.name || ""}`.toLowerCase().includes(search.toLowerCase());
        const matchesRole = roleFilter === "ALL" || member.role === roleFilter;
        const matchesLevel = workloadFilter === "ALL" || level === workloadFilter;
        const matchesProject = projectFilter === "ALL" || assigned.some(issue => String(issue.project?.id ?? "") === projectFilter);
        return matchesSearch && matchesRole && matchesLevel && matchesProject;
    });

    const assignedTotal = issues.filter(issue => who(issue)?.id != null).length;
    const unassignedTotal = issues.filter(issue => who(issue)?.id == null).length;
    const criticalTotal = issues.filter(high).length;
    const statusTotals = Object.fromEntries(STATUSES.map(status => [status, issues.filter(issue => issue.status === status).length]));
    const maximumStatusCount = Math.max(1, ...Object.values(statusTotals));
    const fieldClass = "rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none focus:border-blue-500";

    if (loading) return <div className="flex min-h-[50vh] items-center justify-center text-sm text-gray-400">Loading team workload…</div>;
    if (user?.role !== "PROJECT_MANAGER") return <div className="dfx-access-state mx-auto mt-10 max-w-xl rounded-2xl border border-gray-800 bg-gray-900 p-8 text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-500/10 text-xl text-amber-300">!</div><h1 className="mt-4 text-2xl font-semibold text-white">Access denied</h1><p className="mt-2 text-sm text-gray-400">Team Workload is available to Project Managers. Your current role is {ROLES[user?.role] || user?.role || "unknown"}. Backend authorization remains in effect.</p>{error && <p className="mt-3 text-xs text-red-400">{error}</p>}</div>;

    return <div className="dfx-screen dfx-screen--workload mx-auto max-w-7xl space-y-8">
        <header className="dfx-screen-header"><p className="text-sm font-medium text-blue-400">TEAM OPERATIONS</p><h1 className="mt-2 text-3xl font-semibold text-white">Team Workload</h1><p className="mt-2 text-sm text-gray-400">Monitor assignments, workload, and task distribution across your engineering team.</p></header>
        {error && <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">{error}</div>}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Summary label="Team members" value={members.length} hint="Assignable teammates" tone="blue" />
            <Summary label="Assigned tasks" value={assignedTotal} hint="Across loaded tasks" tone="violet" />
            <Summary label="Unassigned tasks" value={unassignedTotal} hint="Need an owner" tone="amber" />
            <Summary label="High / critical" value={criticalTotal} hint="Across the workspace" tone="red" />
        </section>

        <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 sm:p-6">
            <div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-lg font-semibold text-white">Task status distribution</h2><p className="mt-1 text-sm text-gray-500">Current status counts from the loaded tasks.</p></div><span className="text-xs text-gray-500">Total: {issues.length}</span></div>
            <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{STATUSES.map(status => <div key={status} className="rounded-xl border border-gray-800 bg-gray-950/60 p-4"><div className="flex justify-between gap-4"><span className="text-xs font-medium tracking-wide text-gray-400">{status.replaceAll("_", " ")}</span><span className="text-sm font-semibold text-white">{statusTotals[status]}</span></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-gray-800"><div className="h-full rounded-full bg-blue-500 transition-all" style={{ width: `${statusTotals[status] / maximumStatusCount * 100}%` }} /></div></div>)}</div>
        </section>

        <section className="overflow-hidden rounded-2xl border border-gray-800 bg-gray-900">
            <div className="border-b border-gray-800 p-5 sm:p-6"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-lg font-semibold text-white">Team members</h2><p className="mt-1 text-sm text-gray-500">Workload levels use protected presentation thresholds: 0–3 low, 4–7 medium, 8+ high.</p></div><div className="grid w-full gap-3 sm:grid-cols-2 xl:w-auto xl:grid-cols-4"><input className={fieldClass} value={search} onChange={event => setSearch(event.target.value)} placeholder="Search members…" aria-label="Search members" /><select className={fieldClass} value={roleFilter} onChange={event => setRoleFilter(event.target.value)} aria-label="Filter by role"><option value="ALL">All roles</option>{[...new Set(members.map(member => member.role).filter(Boolean))].map(role => <option key={role} value={role}>{ROLES[role] || role}</option>)}</select><select className={fieldClass} value={workloadFilter} onChange={event => setWorkloadFilter(event.target.value)} aria-label="Filter by workload"><option value="ALL">All workloads</option>{workloadOptions.map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}</select><select className={fieldClass} value={projectFilter} onChange={event => setProjectFilter(event.target.value)} aria-label="Filter by project"><option value="ALL">All projects</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name || `Project ${project.id}`}</option>)}</select></div></div></div>
            {members.length === 0 ? <div className="p-12 text-center"><h3 className="font-medium text-white">No team members available</h3><p className="mt-2 text-sm text-gray-500">The assignable users endpoint returned no members.</p></div> : visibleMembers.length === 0 ? <p className="p-10 text-center text-sm text-gray-400">No team members match these filters.</p> : <div className="divide-y divide-gray-800">{visibleMembers.map(({ member, assigned, statusCounts, level, highPriority }) => <MemberWorkload key={member.id} member={member} assigned={assigned} statusCounts={statusCounts} level={level} highPriority={highPriority} workloadOptions={workloadOptions} />)}</div>}
            {issues.length === 0 && <p className="border-t border-gray-800 px-5 py-4 text-sm text-gray-500">No tasks were returned. Member workload counts are zero.</p>}
        </section>
    </div>;
}

function Summary({ label, value, hint, tone }) {
    const accents = { blue: "bg-blue-500/10 text-blue-300", violet: "bg-violet-500/10 text-violet-300", amber: "bg-amber-500/10 text-amber-300", red: "bg-red-500/10 text-red-300" };
    return <div className="rounded-2xl border border-gray-800 bg-gray-900 p-5"><div className="flex items-start justify-between"><div><p className="text-sm text-gray-400">{label}</p><p className="mt-3 text-3xl font-semibold text-white">{value}</p></div><span className={`rounded-lg px-2.5 py-1 text-xs font-medium ${accents[tone]}`}>LIVE</span></div><p className="mt-3 text-xs text-gray-500">{hint}</p></div>;
}

function MemberWorkload({ member, assigned, statusCounts, level, highPriority, workloadOptions }) {
    const barWidth = `${Math.min(100, assigned.length / 10 * 100)}%`;
    const levels = { LOW: "text-emerald-400", MEDIUM: "text-amber-400", HIGH: "text-red-400" };
    return <article className="p-5 sm:p-6"><div className="flex flex-wrap items-start gap-4"><Avatar name={member.name} src={member.avatarUrl} size={44} /><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-x-3 gap-y-1"><h3 className="font-semibold text-white">{member.name || "Unnamed member"}</h3><span className="rounded-full bg-gray-800 px-2.5 py-1 text-[11px] text-gray-300">{ROLES[member.role] || member.role || "Member"}</span></div><p className="mt-1 text-xs text-gray-500">Team member</p></div><div className="text-right"><p className="text-2xl font-semibold text-white">{assigned.length}</p><p className="text-[11px] uppercase tracking-wider text-gray-500">assigned</p></div></div>
        <div className="mt-5"><div className="mb-2 flex justify-between text-xs"><span className="text-gray-500">Workload <span className="text-gray-600">(presentation only)</span></span><span className={`font-medium ${levels[level]}`}>{dropdownLabel(workloadOptions,level,level)}</span></div><div className="h-2 overflow-hidden rounded-full bg-gray-800"><div className={`h-full rounded-full ${level === "HIGH" ? "bg-red-500" : level === "MEDIUM" ? "bg-amber-500" : "bg-emerald-500"}`} style={{ width: barWidth }} /></div></div>
        <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">{[["Open", statusCounts.OPEN], ["In progress", statusCounts.IN_PROGRESS], ["Code review", statusCounts.CODE_REVIEW], ["Testing", statusCounts.TESTING], ["Resolved", statusCounts.RESOLVED], ["Closed", statusCounts.CLOSED], ["High / critical", highPriority]].map(([label, value]) => <div key={label} className="rounded-lg bg-gray-950/60 px-3 py-2.5"><p className="text-lg font-semibold text-white">{value}</p><p className="mt-0.5 text-[11px] text-gray-500">{label}</p></div>)}</div>
    </article>;
}

export default TeamWorkload;
