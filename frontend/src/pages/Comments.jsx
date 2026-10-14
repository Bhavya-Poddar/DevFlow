import { useEffect, useMemo, useState } from "react";
import Avatar from "../components/Avatar";

const API = "http://localhost:8080/api";
const roleNames = { ADMIN: "Administrator", PROJECT_MANAGER: "Project Manager", DEVELOPER: "Developer", VIEWER: "Viewer" };
const list = value => Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : [];
const dateLabel = value => value ? new Date(value).toLocaleString() : "Date unavailable";

function Comments() {
    const [issues, setIssues] = useState([]);
    const [projects, setProjects] = useState([]);
    const [user, setUser] = useState(null);
    const [issueId, setIssueId] = useState("");
    const [comments, setComments] = useState([]);
    const [commentsForIssue, setCommentsForIssue] = useState("");
    const [search, setSearch] = useState("");
    const [projectFilter, setProjectFilter] = useState("ALL");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [content, setContent] = useState("");
    const [loading, setLoading] = useState(true);
    const [commentsLoading, setCommentsLoading] = useState(true);
    const [creating, setCreating] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [error, setError] = useState("");
    const [commentsError, setCommentsError] = useState("");
    const token = localStorage.getItem("token");

    useEffect(() => {
        let active = true;
        const headers = { Authorization: `Bearer ${token}` };
        Promise.all([
            fetch(`${API}/issues`, { headers }),
            fetch(`${API}/projects`, { headers }),
            fetch(`${API}/users/me`, { headers })
        ]).then(async ([issueResponse, projectResponse, userResponse]) => {
            if (!issueResponse.ok || !projectResponse.ok || !userResponse.ok) throw new Error("Could not load issues, projects, or your account.");
            const [issueData, projectData, userData] = await Promise.all([issueResponse.json(), projectResponse.json(), userResponse.json()]);
            if (!active) return;
            const issueList = list(issueData);
            setIssues(issueList);
            setProjects(list(projectData));
            setUser(userData);
            if (issueList[0]) setIssueId(String(issueList[0].id));
        }).catch(reason => { if (active) setError(reason.message || "Could not load page data."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [token]);

    const visibleIssues = useMemo(() => issues.filter(issue => {
        const projectId = issue.project?.id == null ? "" : String(issue.project.id);
        return (projectFilter === "ALL" || projectId === projectFilter) &&
            (statusFilter === "ALL" || issue.status === statusFilter) &&
            (issue.title || "").toLowerCase().includes(search.toLowerCase());
    }), [issues, projectFilter, statusFilter, search]);
    const selectedIssue = visibleIssues.find(issue => String(issue.id) === issueId) || visibleIssues[0] || null;
    const selectedIssueId = selectedIssue?.id;

    useEffect(() => {
        if (selectedIssueId == null || !token) return undefined;
        let active = true;
        fetch(`${API}/comments/issue/${selectedIssueId}`, { headers: { Authorization: `Bearer ${token}` } })
            .then(async response => {
                if (!response.ok) throw new Error("Could not load comments for this issue.");
                return list(await response.json());
            })
            .then(data => { if (active) { setComments(data); setCommentsForIssue(String(selectedIssueId)); setCommentsLoading(false); } })
            .catch(reason => { if (active) setCommentsError(reason.message); })
            .finally(() => { if (active) setCommentsLoading(false); });
        return () => { active = false; };
    }, [selectedIssueId, token]);

    const canCreate = ["ADMIN", "PROJECT_MANAGER", "DEVELOPER"].includes(user?.role);
    const canDelete = comment => user?.role === "ADMIN" || user?.role === "PROJECT_MANAGER" ||
        (user?.role === "DEVELOPER" && String(comment.user?.id ?? comment.author?.id ?? comment.createdBy?.id ?? comment.userId ?? comment.authorId ?? comment.createdById ?? "") === String(user.id));

    const submitComment = async event => {
        event.preventDefault();
        if (!content.trim() || !selectedIssue || !canCreate) return;
        setCreating(true);
        setCommentsError("");
        try {
            const response = await fetch(`${API}/comments`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
                body: JSON.stringify({ content: content.trim(), issue: { id: selectedIssue.id } })
            });
            if (!response.ok) throw new Error("Comment could not be added. Your access may have changed.");
            setContent("");
            const refreshed = await fetch(`${API}/comments/issue/${selectedIssue.id}`, { headers: { Authorization: `Bearer ${token}` } });
            if (!refreshed.ok) throw new Error("Comment was added, but the updated thread could not be reloaded.");
            setComments(list(await refreshed.json()));
            setCommentsForIssue(String(selectedIssue.id));
        } catch (reason) { setCommentsError(reason.message); }
        finally { setCreating(false); }
    };

    const deleteComment = async comment => {
        setDeletingId(comment.id);
        setCommentsError("");
        try {
            const response = await fetch(`${API}/comments/${comment.id}`, { method: "DELETE", headers: { Authorization: `Bearer ${token}` } });
            if (!response.ok) throw new Error("Comment could not be deleted. The server remains authoritative for access.");
            setComments(previous => previous.filter(item => item.id !== comment.id));
        } catch (reason) { setCommentsError(reason.message); }
        finally { setDeletingId(null); }
    };

    const inputClass = "rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none focus:border-blue-500";

    if (loading) return <PageState title="Loading comments…" />;
    if (error) return <PageError message={error} />;

    return <div className="mx-auto max-w-6xl space-y-7">
        <header><p className="text-sm font-medium text-blue-400">DISCUSSION</p><h1 className="mt-2 text-3xl font-semibold text-white">Comments</h1><p className="mt-2 text-sm text-gray-400">Follow the conversation around your team’s issues.</p></header>

        <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 sm:p-6">
            <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_200px_170px]">
                <input className={inputClass} value={search} onChange={event => setSearch(event.target.value)} placeholder="Search issues…" aria-label="Search issues" />
                <select className={inputClass} value={projectFilter} onChange={event => setProjectFilter(event.target.value)} aria-label="Filter by project"><option value="ALL">All projects</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name || `Project ${project.id}`}</option>)}</select>
                <select className={inputClass} value={statusFilter} onChange={event => setStatusFilter(event.target.value)} aria-label="Filter by status"><option value="ALL">All statuses</option>{[...new Set(issues.map(issue => issue.status).filter(Boolean))].map(status => <option key={status}>{status}</option>)}</select>
            </div>
            <label className="mt-4 block text-xs font-medium uppercase tracking-wider text-gray-500" htmlFor="comment-issue">Selected issue</label>
            <select id="comment-issue" className={`${inputClass} mt-2 w-full`} value={selectedIssue ? String(selectedIssue.id) : ""} onChange={event => { setIssueId(event.target.value); setCommentsError(""); }}>
                {visibleIssues.length === 0 && <option value="">No issues match these filters</option>}
                {visibleIssues.map(issue => <option key={issue.id} value={issue.id}>{issue.title || `Issue #${issue.id}`} · {issue.status || "Unknown status"}</option>)}
            </select>
        </section>

        {selectedIssue ? <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-medium uppercase tracking-wider text-gray-500">ISSUE #{selectedIssue.id}</p><h2 className="mt-2 text-xl font-semibold text-white">{selectedIssue.title || "Untitled issue"}</h2><p className="mt-2 text-sm text-gray-400">{selectedIssue.project?.name || "Project unavailable"} · {selectedIssue.assignedUser?.name || "Unassigned"}</p></div><div className="flex gap-2"><span className="rounded-full bg-gray-800 px-3 py-1 text-xs text-gray-300">{selectedIssue.status || "Unknown status"}</span><span className="rounded-full bg-blue-500/10 px-3 py-1 text-xs font-medium text-blue-300">{selectedIssue.priority || "No priority"}</span></div></div>
        </section> : <div className="rounded-2xl border border-dashed border-gray-700 p-8 text-center text-sm text-gray-400">Select an issue to view its conversation.</div>}

        <section className="rounded-2xl border border-gray-800 bg-gray-900 p-5 sm:p-6">
            <div className="mb-5 flex items-center justify-between gap-4"><div><h2 className="text-lg font-semibold text-white">Discussion</h2><p className="mt-1 text-sm text-gray-500">{comments.length} {comments.length === 1 ? "comment" : "comments"}</p></div>{user && <span className="rounded-full border border-gray-700 px-3 py-1 text-xs text-gray-400">{roleNames[user.role] || user.role}</span>}</div>
            {commentsError && <div role="alert" className="mb-4 rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">{commentsError}</div>}
            {!selectedIssue ? <p className="rounded-xl bg-gray-800/50 px-4 py-8 text-center text-sm text-gray-400">Select an issue to view comments.</p> : commentsLoading || commentsForIssue !== String(selectedIssue.id) ? <p className="py-8 text-center text-sm text-gray-500">Loading discussion…</p> : comments.length === 0 ? <p className="rounded-xl bg-gray-800/50 px-4 py-8 text-center text-sm text-gray-400">No comments yet. Start the conversation below.</p> : <div className="space-y-3">
                {comments.map(comment => {
                    const author = comment.user || comment.author || {};
                    return <article key={comment.id} className="rounded-xl border border-gray-800 bg-gray-950/60 p-4">
                        <div className="flex items-start gap-3"><Avatar name={author.name} src={author.avatarUrl} size={40} /><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center justify-between gap-2"><div><p className="text-sm font-semibold text-white">{author.name || "Unknown user"}</p><p className="mt-0.5 text-xs text-gray-500">{roleNames[author.role] || author.role || "Member"} · {dateLabel(comment.createdAt)}</p></div>{canDelete(comment) && <button type="button" disabled={deletingId === comment.id} onClick={() => deleteComment(comment)} className="rounded-md px-2 py-1 text-xs text-red-400 hover:bg-red-500/10 disabled:opacity-50">{deletingId === comment.id ? "Deleting…" : "Delete"}</button>}</div><p className="mt-3 whitespace-pre-wrap break-words text-sm leading-relaxed text-gray-300">{comment.content || ""}</p></div></div>
                    </article>;
                })}
            </div>}
            {canCreate ? <form onSubmit={submitComment} className="mt-5 border-t border-gray-800 pt-5"><label htmlFor="new-comment" className="mb-2 block text-sm font-medium text-gray-300">Add a comment</label><div className="flex flex-col gap-3 sm:flex-row sm:items-end"><textarea id="new-comment" rows="3" value={content} onChange={event => setContent(event.target.value)} placeholder="Share an update or ask a question…" className={`${inputClass} min-h-24 flex-1 resize-y`} /><button disabled={!content.trim() || creating || !selectedIssue} className="rounded-lg bg-blue-600 px-5 py-3 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">{creating ? "Posting…" : "Post comment"}</button></div></form> : <p className="mt-5 border-t border-gray-800 pt-5 text-sm text-gray-500">Your Viewer role can read this discussion but cannot add or delete comments.</p>}
        </section>
    </div>;
}

function PageState({ title }) { return <div className="flex min-h-[40vh] items-center justify-center text-sm text-gray-400">{title}</div>; }
function PageError({ message }) { return <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 p-5 text-sm text-red-300">{message}</div>; }

export default Comments;
