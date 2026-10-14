import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

const API = "http://localhost:8080/api";
const list = value => Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : [];
const assigneeOf = issue => issue.assignedUser || issue.assignee || issue.assignedTo || null;
const dateLabel = value => value && !Number.isNaN(new Date(value).getTime()) ? new Date(value).toLocaleDateString() : null;
const priorityStyles = { LOW: "border-slate-500/30 bg-slate-500/10 text-slate-300", MEDIUM: "border-blue-500/30 bg-blue-500/10 text-blue-300", HIGH: "border-amber-500/30 bg-amber-500/10 text-amber-300", CRITICAL: "border-red-500/30 bg-red-500/10 text-red-300" };

function CodeReviews() {
    const navigate = useNavigate();
    const [user, setUser] = useState(null);
    const [issues, setIssues] = useState([]);
    const [search, setSearch] = useState("");
    const [priority, setPriority] = useState("ALL");
    const [project, setProject] = useState("ALL");
    const [assignedToMe, setAssignedToMe] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        const headers = { Authorization: `Bearer ${localStorage.getItem("token")}` };
        (async () => {
            const response = await fetch(`${API}/users/me`, { headers });
            if (!response.ok) throw new Error("Could not verify your account.");
            const current = await response.json();
            if (!active) return;
            setUser(current);
            const issueResponse = await fetch(`${API}/issues`, { headers });
            if (!issueResponse.ok) throw new Error("Could not load issues awaiting review.");
            const data = await issueResponse.json();
            if (active) setIssues(list(data));
        })().catch(reason => { if (active) setError(reason.message || "Could not load Code Reviews."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const reviews = useMemo(() => issues.filter(issue => issue.status === "CODE_REVIEW"), [issues]);
    const isMine = issue => {
        const assigned = assigneeOf(issue);
        const assignedId = typeof assigned === "object" ? assigned.id : assigned;
        const assignedEmail = typeof assigned === "object" ? assigned.email : null;
        const explicitId = issue.assignedUserId ?? issue.assigneeId ?? issue.assignedToId ?? issue.assignedTo?.id;
        return (user?.id != null && String(assignedId ?? explicitId ?? "") === String(user.id)) ||
            (user?.email && String(assignedEmail || "").toLowerCase() === user.email.toLowerCase());
    };
    const projects = [...new Map(reviews.filter(issue => issue.project?.id != null).map(issue => [String(issue.project.id), issue.project])).values()];
    const filteredReviews = reviews.filter(issue => {
        const text = `${issue.title || ""} ${issue.description || ""} ${issue.id ?? ""}`.toLowerCase();
        return text.includes(search.toLowerCase()) &&
            (priority === "ALL" || issue.priority === priority) &&
            (project === "ALL" || String(issue.project?.id ?? "") === project) &&
            (!assignedToMe || isMine(issue));
    });
    const high = reviews.filter(issue => issue.priority === "HIGH").length;
    const critical = reviews.filter(issue => issue.priority === "CRITICAL").length;
    const mine = reviews.filter(isMine).length;
    const inputClass = "rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none focus:border-blue-500";

    if (loading) return <div className="flex min-h-[50vh] items-center justify-center text-sm text-gray-400">Loading code reviews…</div>;
    const descriptions = {
        ADMIN: "System-wide code-review issues from the available workspace data.",
        PROJECT_MANAGER: "Project code-review queue across the projects available to you.",
        DEVELOPER: "Engineering review queue, with an Assigned to Me filter for your work.",
        VIEWER: "A read-only view of issues currently marked for code review."
    };

    return <div className="mx-auto max-w-7xl space-y-8">
        <header><p className="text-sm font-medium text-blue-400">{user?.role === "ADMIN" ? "SYSTEM REVIEW QUEUE" : user?.role === "PROJECT_MANAGER" ? "PROJECT REVIEW QUEUE" : user?.role === "VIEWER" ? "READ-ONLY REVIEW QUEUE" : "ENGINEERING QUALITY"}</p><h1 className="mt-2 text-3xl font-semibold text-white">Code Reviews</h1><p className="mt-2 text-sm text-gray-400">{descriptions[user?.role] || "Review issues waiting for engineering validation."}</p></header>
        {error && <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">{error}</div>}
        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><Summary label="Awaiting Review" value={reviews.length} /><Summary label="High Priority" value={high} tone="amber" /><Summary label="Critical" value={critical} tone="red" /><Summary label="Assigned to Me" value={mine} tone="blue" /></section>
        <section className="overflow-hidden rounded-2xl border border-gray-800 bg-gray-900">
            <div className="border-b border-gray-800 p-5 sm:p-6"><div className="flex flex-col gap-3 xl:flex-row"><input className={`${inputClass} flex-1`} value={search} onChange={event => setSearch(event.target.value)} placeholder="Search reviews…" aria-label="Search reviews" /><select className={inputClass} value={priority} onChange={event => setPriority(event.target.value)} aria-label="Filter by priority"><option value="ALL">All priorities</option>{["LOW", "MEDIUM", "HIGH", "CRITICAL"].map(item => <option key={item}>{item}</option>)}</select><select className={inputClass} value={project} onChange={event => setProject(event.target.value)} aria-label="Filter by project"><option value="ALL">All projects</option>{projects.map(item => <option key={item.id} value={item.id}>{item.name || `Project ${item.id}`}</option>)}</select><label className="flex cursor-pointer items-center gap-2 rounded-lg border border-gray-700 px-4 py-2.5 text-sm text-gray-300"><input type="checkbox" checked={assignedToMe} onChange={event => setAssignedToMe(event.target.checked)} className="accent-blue-500" />Assigned to me</label></div></div>
            {reviews.length === 0 ? <div className="p-12 text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/10 text-xl text-emerald-300">✓</div><h2 className="mt-4 font-medium text-white">No issues are currently waiting for code review.</h2><p className="mt-2 text-sm text-gray-500">Issues with CODE_REVIEW status will appear here.</p></div> : filteredReviews.length === 0 ? <p className="p-10 text-center text-sm text-gray-400">No reviews match your filters.</p> : <div className="grid gap-4 p-5 sm:p-6 xl:grid-cols-2">{filteredReviews.map(issue => <ReviewCard key={issue.id} issue={issue} assignedToMe={isMine(issue)} onView={() => navigate("/issues")} readOnly={user?.role === "VIEWER"} />)}</div>}
        </section>
    </div>;
}

function Summary({ label, value, tone = "gray" }) {
    const styles = { gray: "text-white", amber: "text-amber-300", red: "text-red-300", blue: "text-blue-300" };
    return <div className="rounded-2xl border border-gray-800 bg-gray-900 p-5"><p className="text-sm text-gray-500">{label}</p><p className={`mt-3 text-3xl font-semibold ${styles[tone]}`}>{value}</p></div>;
}

function ReviewCard({ issue, assignedToMe, onView, readOnly }) {
    const assignment = assigneeOf(issue);
    const assignee = typeof assignment === "object" ? assignment.name || assignment.email : assignment ? `User #${assignment}` : null;
    const priorityName = String(issue.priority || "UNKNOWN").toUpperCase();
    return <article className={`rounded-xl border bg-gray-950/60 p-5 ${priorityName === "CRITICAL" ? "border-red-500/40" : priorityName === "HIGH" ? "border-amber-500/30" : "border-gray-800"}`}><div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0"><p className="text-xs font-medium uppercase tracking-wider text-gray-500">Issue #{issue.id ?? "—"}</p><h2 className="mt-2 text-lg font-semibold text-white">{issue.title || "Untitled issue"}</h2></div><span className={`rounded-full border px-3 py-1 text-xs font-semibold ${priorityStyles[priorityName] || "border-gray-700 bg-gray-800 text-gray-300"}`}>{priorityName}</span></div><p className="mt-3 line-clamp-3 whitespace-pre-wrap text-sm leading-relaxed text-gray-400">{issue.description || "No description provided."}</p><div className="mt-4 flex flex-wrap gap-2"><span className="rounded-full bg-blue-500/10 px-3 py-1 text-xs text-blue-300">CODE REVIEW</span>{assignedToMe && <span className="rounded-full bg-emerald-500/10 px-3 py-1 text-xs text-emerald-300">Assigned to me</span>}</div><dl className="mt-5 grid grid-cols-2 gap-3 border-t border-gray-800 pt-4 text-sm"><Info label="Project" value={issue.project?.name || "No project"} /><Info label="Assigned developer" value={assignee || "Unassigned"} /><Info label="Sprint" value={issue.sprint?.name || "No sprint"} />{dateLabel(issue.updatedAt) && <Info label="Updated" value={dateLabel(issue.updatedAt)} />}{!issue.updatedAt && dateLabel(issue.createdAt) && <Info label="Created" value={dateLabel(issue.createdAt)} />}</dl>{!readOnly && <button type="button" onClick={onView} className="mt-5 w-full rounded-lg border border-gray-700 px-4 py-2.5 text-sm font-medium text-gray-300 transition hover:border-blue-500 hover:bg-blue-500/10 hover:text-blue-300">View Issue</button>}</article>;
}

function Info({ label, value }) { return <div className="min-w-0"><dt className="text-xs text-gray-500">{label}</dt><dd className="mt-1 truncate text-gray-300">{value}</dd></div>; }

export default CodeReviews;
