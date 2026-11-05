import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Avatar from "../components/Avatar";
import { apiUrl } from "../services/apiConfig";
import { responseErrorMessage } from "../utils/responseError";
import "./Dashboard.css";

const statusColors = {
    OPEN: "#7186ff",
    IN_PROGRESS: "#38bdf8",
    CODE_REVIEW: "#a78bfa",
    READY_FOR_TESTING: "#fbbf24",
    TESTING: "#fb923c",
    RESOLVED: "#34d399"
};

async function requestJson(path, token, signal) {
    const response = await fetch(apiUrl(path), {
        headers: { Authorization: `Bearer ${token}` },
        signal
    });
    if (!response.ok) {
        throw new Error(await responseErrorMessage(response, "Could not load this dashboard section."));
    }
    return response.json();
}

function useDashboardRequest(path, token, query = "", enabled = true) {
    const [state, setState] = useState({ key: "", data: null, error: "" });
    const requestKey = `${path}?${query}`;
    useEffect(() => {
        if (!token || !enabled) return undefined;
        const controller = new AbortController();
        requestJson(`${path}${query ? `?${query}` : ""}`, token, controller.signal)
            .then(data => setState({ key: requestKey, data, error: "" }))
            .catch(error => {
                if (error.name !== "AbortError") setState({ key: requestKey, data: null, error: error.message });
            });
        return () => controller.abort();
    }, [path, token, query, enabled, requestKey]);
    if (!enabled) return { data: null, loading: false, error: "", disabled: true };
    return state.key === requestKey
        ? { ...state, loading: false, disabled: false }
        : { data: null, loading: true, error: "", disabled: false };
}
function Dashboard() {
    const navigate = useNavigate();
    const token = localStorage.getItem("token");
    const [projectId, setProjectId] = useState("");
    const [sprintId, setSprintId] = useState("");
    const [user, setUser] = useState(null);
    const [userError, setUserError] = useState("");
    const [canReviewApprovals, setCanReviewApprovals] = useState(false);
    const isAdmin = user?.role === "ADMIN";
    const roleDashboard = {
        ADMIN: { title: "Platform overview", description: "A global view of delivery, testing, sprint progress, and work at risk." },
        PROJECT_MANAGER: { title: "Team delivery", description: "Track progress, workload, testing, and delivery risk across projects you manage or can access." },
        DEVELOPER: { title: "Development workspace", description: "Follow accessible project work, sprint progress, and tasks that need attention." },
        TESTER: { title: "Testing & team context", description: "See your authorized project context alongside testing outcomes and delivery risks." },
        VIEWER: { title: "Project overview", description: "Review current progress and delivery signals for projects available to your account." }
    }[user?.role] || { title: "Engineering insights", description: "Delivery flow, testing outcomes, sprint progress, and current bottlenecks." };
    const [projects, setProjects] = useState({ data: [], loading: true, error: "" });
    const [sprints, setSprints] = useState({ data: [], loading: true, error: "" });
    const [approvalQueue, setApprovalQueue] = useState({ data: null, loading: true, error: "" });
    const [approvalSaving, setApprovalSaving] = useState(false);
    const [approvalMessage, setApprovalMessage] = useState("");
    const [approvalError, setApprovalError] = useState("");
    const [rejectingApproval, setRejectingApproval] = useState(null);
    const [rejectionRemark, setRejectionRemark] = useState("");

    const loadApprovals = useCallback(async (signal) => {
        try {
            const data = await requestJson("/api/approvals/pending", token, signal);
            setApprovalQueue({ data, loading: false, error: "" });
        } catch (error) {
            if (error.name !== "AbortError") setApprovalQueue(previous => ({ ...previous, loading: false, error: error.message }));
        }
    }, [token]);

    useEffect(() => {
        if (!token) return undefined;
        const controller = new AbortController();
        const refresh = () => void loadApprovals(controller.signal);
        refresh();
        window.addEventListener("devflow-approvals-changed", refresh);
        window.addEventListener("devflow-notification-received", refresh);
        const interval = window.setInterval(refresh, 60000);
        return () => { controller.abort(); window.clearInterval(interval); window.removeEventListener("devflow-approvals-changed", refresh); window.removeEventListener("devflow-notification-received", refresh); };
    }, [token, loadApprovals]);

    const decideApproval = async (item, decision) => {
        if (decision === "REJECTED" && !rejectionRemark.trim()) {
            setApprovalError("Add a remark before rejecting this task.");
            return;
        }
        setApprovalSaving(true);
        setApprovalError("");
        setApprovalMessage("");
        try {
            const response = await fetch(apiUrl(`/api/issues/${encodeURIComponent(item.taskId)}/approvals/${encodeURIComponent(item.stage)}`), {
                method: "POST",
                headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
                body: JSON.stringify({ decision, remark: decision === "REJECTED" ? rejectionRemark.trim() : "" })
            });
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not save the approval decision."));
            setApprovalMessage(decision === "APPROVED" ? "Approval recorded." : "Task returned to the developer with your remark.");
            setRejectingApproval(null);
            setRejectionRemark("");
            await loadApprovals();
            window.dispatchEvent(new Event("devflow-approvals-changed"));
        } catch (error) {
            setApprovalError(error.message || "Could not save the approval decision.");
        } finally {
            setApprovalSaving(false);
        }
    };

    useEffect(() => {
        if (!token) {
            navigate("/login", { replace: true });
            return undefined;
        }
        const controller = new AbortController();
        requestJson("/api/users/me", token, controller.signal)
            .then(setUser)
            .catch(error => {
                if (error.name !== "AbortError") setUserError(error.message);
            });
        requestJson("/api/users/me/permissions", token, controller.signal)
            .then(data => {
                const permissions = [
                    ...(data?.global || []),
                    ...(data?.projects || []).flatMap(scope => scope.permissions || []),
                    ...(data?.teams || []).flatMap(scope => scope.permissions || [])
                ];
                setCanReviewApprovals(permissions.some(permission => ["ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER"].includes(permission)));
            })
            .catch(error => { if (error.name !== "AbortError") setCanReviewApprovals(false); });
        return () => controller.abort();
    }, [token, navigate]);

    useEffect(() => {
        if (!token) return undefined;
        const projectsController = new AbortController();
        const sprintsController = new AbortController();
        requestJson("/api/projects", token, projectsController.signal)
            .then(data => setProjects({ data: Array.isArray(data) ? data : [], loading: false, error: "" }))
            .catch(error => { if (error.name !== "AbortError") setProjects({ data: [], loading: false, error: error.message }); });
        requestJson("/api/sprints", token, sprintsController.signal)
            .then(data => setSprints({ data: Array.isArray(data) ? data : [], loading: false, error: "" }))
            .catch(error => { if (error.name !== "AbortError") setSprints({ data: [], loading: false, error: error.message }); });
        return () => { projectsController.abort(); sprintsController.abort(); };
    }, [token]);

    const effectiveProjectId = isAdmin ? projectId : (projectId || (projects.data.length ? String(projects.data[0].id) : ""));
    const dashboardEnabled = isAdmin || Boolean(effectiveProjectId || sprintId);
    const query = useMemo(() => {
        const params = new URLSearchParams();
        if (effectiveProjectId) params.set("projectId", effectiveProjectId);
        if (sprintId) params.set("sprintId", sprintId);
        return params.toString();
    }, [effectiveProjectId, sprintId]);
    const sprintQuery = effectiveProjectId ? `projectId=${encodeURIComponent(effectiveProjectId)}` : "";
    const overview = useDashboardRequest("/api/dashboard/overview", token, query, dashboardEnabled);
    const projectProgress = useDashboardRequest("/api/dashboard/project-progress", token, query, dashboardEnabled);
    const workload = useDashboardRequest("/api/dashboard/workload", token, query, dashboardEnabled);
    const testing = useDashboardRequest("/api/dashboard/testing", token, query, dashboardEnabled);
    const bottlenecks = useDashboardRequest("/api/dashboard/bottlenecks", token, query, dashboardEnabled);
    const activeSprints = useDashboardRequest("/api/dashboard/sprints", token, sprintQuery, dashboardEnabled);
    const workloadRisk = useDashboardRequest("/api/dashboard/workload-risk", token, query, dashboardEnabled);
    const burndown = useDashboardRequest(
        sprintId ? `/api/reports/sprints/${encodeURIComponent(sprintId)}/burndown` : "",
        token,
        "",
        Boolean(sprintId)
    );

    const visibleSprints = useMemo(() => (sprints.data || []).filter(sprint =>
        !effectiveProjectId || String(sprint.project?.id ?? sprint.projectId ?? "") === String(effectiveProjectId)
    ), [sprints.data, effectiveProjectId]);

    const scope = sprintId
        ? `${visibleSprints.find(sprint => String(sprint.id) === String(sprintId))?.name || `Sprint ${sprintId}`} · filtered view`
        : effectiveProjectId
            ? `${projects.data.find(project => String(project.id) === String(effectiveProjectId))?.name || "Selected project"} · filtered view`
            : isAdmin ? "All projects · global view" : "Choose an accessible project";

    const openTask = id => navigate(`/issues?issueId=${encodeURIComponent(id)}`);

    return <main className="df-dashboard smart-dashboard">
        <header className="sd-header">
            <div>
                <p className="sd-eyebrow"><span /> ENGINEERING INTELLIGENCE</p>
                <h1>{roleDashboard.title}</h1>
                <p>{roleDashboard.description}</p>
            </div>
            <div className="sd-header-meta">
                <span className="sd-scope">{scope}</span>
                <span className="sd-greeting">{user ? `Welcome, ${user.name}` : userError ? "Profile unavailable" : "Loading profile…"}</span>
                <div className="sd-actions">
                    <button type="button" className="sd-button sd-button-quiet" onClick={() => navigate(user?.role === "TESTER" ? "/testing" : "/issues")}>{user?.role === "TESTER" ? "Testing queue" : "Browse tasks"} <span aria-hidden="true">↗</span></button>
                    <button type="button" className="sd-button sd-button-primary" onClick={() => navigate("/private-teams")}>My Teams <span aria-hidden="true">→</span></button>
                </div>
            </div>
        </header>

        {canReviewApprovals && <section id="pending-approvals" className="mb-6 scroll-mt-24 rounded-2xl border border-violet-400/20 bg-gradient-to-br from-violet-500/[0.08] to-blue-500/[0.04] p-5 sm:p-6" aria-labelledby="pending-approvals-heading">
            <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                <div><p className="sd-kicker">REVIEW QUEUE</p><h2 id="pending-approvals-heading" className="text-xl font-semibold text-white">Pending approvals</h2><p className="mt-1 text-sm text-gray-400">Testing passed. Review work waiting for your authorized decision.</p></div>
                <div className="flex gap-2 text-xs"><span className="rounded-full border border-violet-400/20 bg-violet-400/10 px-3 py-1.5 text-violet-200">Team Lead · {approvalQueue.data?.teamLeadPendingCount ?? "—"}</span><span className="rounded-full border border-blue-400/20 bg-blue-400/10 px-3 py-1.5 text-blue-200">Manager · {approvalQueue.data?.projectManagerPendingCount ?? "—"}</span></div>
            </div>
            {approvalQueue.loading && !approvalQueue.data ? <p role="status" className="rounded-xl border border-gray-800 bg-gray-950/40 p-4 text-sm text-gray-400">Loading approval queue…</p>
                : approvalQueue.error && !approvalQueue.data ? <div role="alert" className="rounded-xl border border-red-500/20 bg-red-500/5 p-4 text-sm text-red-200">Could not load pending approvals: {approvalQueue.error}<button type="button" onClick={() => void loadApprovals()} className="ml-3 underline underline-offset-2">Retry</button></div>
                    : !approvalQueue.data?.pendingApprovals?.length ? <p className="rounded-xl border border-gray-800 bg-gray-950/40 p-4 text-sm text-gray-400">No tasks are waiting for your approval in projects you can manage.</p>
                        : <div className="grid gap-3">{approvalQueue.data.pendingApprovals.map(item => <article key={`${item.taskId}-${item.stage}`} className="rounded-xl border border-gray-800 bg-gray-950/55 p-4">
                            <div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold text-gray-100">{item.taskName || `Task #${item.taskId}`}</h3><span className="rounded-full bg-gray-800 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-violet-200">{item.stage === "TEAM_LEAD" ? "Team Lead review" : "Manager review"}</span></div><p className="mt-1 text-sm text-gray-400">{item.projectName || "Project unavailable"} · Developer: {item.developerName || "Unassigned"} · Tester: {item.testerName || "Unavailable"}</p><p className="mt-1 text-xs text-gray-500">Test {item.testingResult || "result unavailable"} · completed {formatApprovalDate(item.testingCompletedAt)} · waiting {formatWait(item.waitingSeconds)}{item.teamLeadName ? ` · Team Lead: ${item.teamLeadName}${item.teamLeadApprovedAt ? `, ${formatApprovalDate(item.teamLeadApprovedAt)}` : ""}` : ""}</p></div><span className="rounded-lg border border-emerald-500/20 bg-emerald-500/5 px-3 py-2 text-xs text-emerald-200">PASS</span></div>
                            <div className="mt-4 flex flex-wrap gap-2"><button type="button" onClick={() => openTask(item.taskId)} className="sd-button sd-button-quiet">View Task</button><button type="button" disabled={approvalSaving} onClick={() => void decideApproval(item, "APPROVED")} className="sd-button sd-button-primary disabled:opacity-50">{item.stage === "PROJECT_MANAGER" ? "Approve & Resolve" : "Approve"}</button><button type="button" disabled={approvalSaving} onClick={() => { setRejectingApproval(item); setRejectionRemark(""); setApprovalError(""); }} className="rounded-lg border border-red-500/30 px-3 py-2 text-sm text-red-200 hover:bg-red-500/10 disabled:opacity-50">Reject</button></div>
                        </article>)}</div>}
            {approvalMessage && <p role="status" className="mt-3 text-sm text-emerald-300">{approvalMessage}</p>}{approvalError && !rejectingApproval && <p role="alert" className="mt-3 text-sm text-red-300">{approvalError}</p>}
        </section>}

        <section className="sd-filterbar" aria-label="Dashboard filters">
            <div><label htmlFor="dashboard-project">Project</label><select id="dashboard-project" value={effectiveProjectId} onChange={event => { setProjectId(event.target.value); setSprintId(""); }}>
                {isAdmin ? <option value="">All projects</option> : <option value="">Choose a project</option>}{projects.data.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
            </select>{projects.loading && <small>Loading projects…</small>}{projects.error && <small className="sd-error-text">{projects.error}</small>}</div>
            <div><label htmlFor="dashboard-sprint">Sprint</label><select id="dashboard-sprint" value={sprintId} onChange={event => setSprintId(event.target.value)} disabled={sprints.loading || !visibleSprints.length}>
                <option value="">All sprints</option>{visibleSprints.map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name}</option>)}
            </select>{sprints.loading && <small>Loading sprints…</small>}{sprints.error && <small className="sd-error-text">{sprints.error}</small>}</div>
            {isAdmin && <button type="button" className="sd-reset" onClick={() => { setProjectId(""); setSprintId(""); }}>Clear filters</button>}
            <p>{isAdmin ? "Metrics follow the selected scope; administrators can view global metrics." : "Metrics are limited to projects and sprints your account can access."}</p>
        </section>

        {rejectingApproval && <div role="presentation" className="fixed inset-0 z-[80] grid place-items-center bg-black/70 p-4" onMouseDown={event => { if (event.target === event.currentTarget && !approvalSaving) setRejectingApproval(null); }}><section role="dialog" aria-modal="true" aria-labelledby="approval-reject-title" className="w-full max-w-lg rounded-2xl border border-gray-700 bg-gray-900 p-5 shadow-2xl sm:p-6"><p className="text-xs font-semibold uppercase tracking-wider text-red-300">Required rejection note</p><h2 id="approval-reject-title" className="mt-2 text-lg font-semibold text-white">Return {rejectingApproval.taskName || `Task #${rejectingApproval.taskId}`} to the developer?</h2><p className="mt-2 text-sm text-gray-400">A remark is required and will be recorded with this decision.</p><label className="mt-4 block text-sm text-gray-300" htmlFor="approval-rejection-remark">Remark<textarea id="approval-rejection-remark" rows={4} value={rejectionRemark} onChange={event => setRejectionRemark(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2 text-gray-100 outline-none focus:border-violet-400" /></label>{approvalError && <p role="alert" className="mt-2 text-sm text-red-300">{approvalError}</p>}<div className="mt-5 flex justify-end gap-2"><button type="button" disabled={approvalSaving} onClick={() => { setRejectingApproval(null); setApprovalError(""); }} className="sd-button sd-button-quiet">Cancel</button><button type="button" disabled={approvalSaving || !rejectionRemark.trim()} onClick={() => void decideApproval(rejectingApproval, "REJECTED")} className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">{approvalSaving ? "Saving…" : "Reject task"}</button></div></section></div>}

        {!isAdmin && !projects.loading && !projects.data.length && !projects.error && <div role="status" className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-700 bg-gray-900/70 p-4 text-sm text-gray-300"><span>No projects are available to your account. Check your team membership or ask a team manager about project access.</span><button type="button" className="sd-text-button" onClick={() => navigate("/private-teams")}>Open My Teams <span aria-hidden="true">→</span></button></div>}
        <div className="sd-overview-grid">
            <SectionState state={overview}>
                {data => <>
                    <div className="sd-section-head"><div><p className="sd-kicker">DELIVERY OVERVIEW</p><h2>Work at a glance</h2></div><span className="sd-section-caption">LIVE TASK DATA</span></div>
                    <div className="sd-kpi-grid">
                        <Metric label="Total tasks" value={data.totalTasks} note="In the selected scope" tone="blue" />
                        <Metric label="Completed" value={data.completedTasks} note="Status: Resolved" tone="green" />
                        <Metric label="In progress" value={data.inProgressTasks} note="Status: In Progress" tone="cyan" />
                        <Metric label="Overdue (sprint)" value={data.overdueTasks} note="Unresolved in ended sprints" tone="amber" />
                        <Metric label="Completion rate" value={`${data.completionRate}%`} note={`${data.completedTasks} of ${data.totalTasks} resolved`} tone="violet" />
                    </div>
                    <div className="sd-status-block"><div className="sd-section-head sd-subhead"><div><p className="sd-kicker">WORKFLOW</p><h3>Task status distribution</h3></div><span>{data.totalTasks} tasks</span></div>
                        {data.totalTasks ? <StatusDistribution statuses={data.statusDistribution || []} total={data.totalTasks} /> : <p className="sd-inline-empty">No task status data yet.</p>}
                    </div>
                </>}
            </SectionState>

            <SectionState state={testing} empty={!testing.data?.completedExecutions} emptyText="No completed PASS or FAIL executions in this scope yet.">
                {data => <div className="sd-testing-content"><div className="sd-section-head"><div><p className="sd-kicker">QUALITY SIGNAL</p><h2>Testing outcomes</h2></div><span className="sd-ring-mark">✓</span></div>
                    <div className="sd-testing-rate"><strong>{data.passRate}%</strong><span>pass rate</span></div>
                    <div className="sd-outcome-track" aria-label={`Passed ${data.passedExecutions}, failed ${data.failedExecutions}`}><span style={{ width: `${data.passRate}%` }} /></div>
                    <div className="sd-outcome-grid"><div><span>Passed</span><strong>{data.passedExecutions}</strong></div><div><span>Failed</span><strong>{data.failedExecutions}</strong></div><div><span>Denominator</span><strong>{data.completedExecutions}</strong></div></div>
                    <p className="sd-footnote">{data.denominator}</p>
                </div>}
            </SectionState>
        </div>

        <div className="sd-content-grid">
            <SectionState state={projectProgress} empty={!projectProgress.data?.projects?.length} emptyText="No project progress to show in this scope.">
                {data => <div className="sd-panel-content"><div className="sd-section-head"><div><p className="sd-kicker">PORTFOLIO</p><h2>Project progress</h2><p>Resolved tasks / total tracked tasks</p></div></div>
                    <div className="sd-project-list">{data.projects.map(project => <div className="sd-project-row" key={project.projectId}>
                        <div className="sd-project-title"><span>{String(project.projectName || "P").trim().charAt(0).toUpperCase()}</span><div><strong>{project.projectName}</strong><small>{project.completedIssues} of {project.totalIssues} resolved</small></div></div>
                        <div className="sd-progress"><strong>{project.completionPercent}%</strong><div><span style={{ width: `${project.completionPercent}%` }} /></div></div>
                    </div>)}</div>
                </div>}
            </SectionState>

            <SectionState state={workload} empty={!workload.data?.members?.length} emptyText="No developer or tester assignments in this scope.">
                {data => <div className="sd-panel-content"><div className="sd-section-head"><div><p className="sd-kicker">TEAM CAPACITY</p><h2>Assignment workload</h2><p>Current assigned task counts, grouped by role</p></div></div>
                    <div className="sd-workload-list">{data.members.slice(0, 8).map(person => <div className="sd-workload-row" key={`${person.role}-${person.userId}`}>
                        <Avatar name={person.name} src={person.avatarUrl} size={38} />
                        <div className="sd-workload-person"><div><strong>{person.name}</strong><span>{person.role.replaceAll("_", " ")}</span></div><div className="sd-workload-track"><span style={{ width: `${Math.max(4, person.assignedIssues * 100 / Math.max(1, ...data.members.map(member => member.assignedIssues)))}%` }} /></div></div>
                        <strong className="sd-assignment-count">{person.assignedIssues}<small>tasks</small></strong>
                    </div>)}</div>
                </div>}
            </SectionState>
        </div>

        <div className="sd-content-grid sd-content-grid-wide">
            <SectionState state={activeSprints} empty={!activeSprints.data?.activeSprints?.length} emptyText="No active sprint in this scope.">
                {data => <div className="sd-panel-content"><div className="sd-section-head"><div><p className="sd-kicker">CURRENT CYCLE</p><h2>Active sprint progress</h2><p>Live counts use current issue status; burndown uses recorded snapshots only.</p></div></div>
                    <div className="sd-sprint-list">{data.activeSprints.map(sprint => <article className={`sd-sprint-card ${String(sprint.sprintId) === String(sprintId) ? "is-selected" : ""}`} key={sprint.sprintId}>
                        <div className="sd-sprint-top"><div><strong>{sprint.sprintName}</strong><small>{sprint.projectName || "Project"} · {formatDate(sprint.startDate)} – {formatDate(sprint.endDate)}</small></div><span>{sprint.status.replaceAll("_", " ")}</span></div>
                        <div className="sd-sprint-metrics"><strong>{sprint.completionRate}%<small>complete</small></strong><span>{sprint.completedTasks} / {sprint.totalTasks} resolved</span></div>
                        <div className="sd-progress"><div><span style={{ width: `${sprint.completionRate}%` }} /></div></div>
                        <button type="button" className="sd-text-button" onClick={() => setSprintId(String(sprint.sprintId))}>View burndown history <span aria-hidden="true">→</span></button>
                    </article>)}</div>
                </div>}
            </SectionState>
            <SectionState state={burndown} empty={!burndown.data?.snapshots?.length} emptyText={sprintId ? "No recorded burndown snapshots yet. Tracking begins when the first snapshot is collected." : "Choose a sprint to view its recorded burndown history."}>
                {data => <BurndownChart data={data} />}
            </SectionState>
        </div>

        <SectionState state={workloadRisk} empty={!workloadRisk.data?.overdueTasks?.length && !workloadRisk.data?.approachingDeadlineTasks?.length && !workloadRisk.data?.unresolvedByAssignee?.length && !workloadRisk.data?.sprintRisks?.length} emptyText="No unresolved deadline or sprint workload risks in this scope.">
            {data => <div className="sd-panel-content"><div className="sd-section-head"><div><p className="sd-kicker">CAPACITY & DELIVERY RISK</p><h2>Sprint workload & risk</h2><p>Prioritize unresolved work by deadline, owner, and sprint exposure.</p></div><span className="sd-section-caption">AS OF {formatDate(data.asOf)}</span></div>
                <div className="sd-risk-deadlines">
                    <RiskTaskList title="Overdue" tasks={data.overdueTasks} tone="overdue" onOpen={openTask} emptyText="No unresolved tasks are past due." />
                    <RiskTaskList title="Due in the next 7 days" tasks={data.approachingDeadlineTasks} tone="approaching" onOpen={openTask} emptyText="No unresolved tasks are approaching their due date." />
                </div>
                <div className="sd-risk-columns">
                    <section className="sd-risk-subsection"><div className="sd-risk-subhead"><h3>Unresolved by assignee</h3><span>Primary task owner</span></div>
                        {data.unresolvedByAssignee?.length ? <div className="sd-risk-assignees">{data.unresolvedByAssignee.slice(0, 8).map(person => <div className="sd-risk-assignee" key={person.userId ?? "unassigned"}>
                            {person.userId ? <Avatar name={person.name} src={person.avatarUrl} size={32} /> : <span className="sd-risk-unassigned" aria-hidden="true">—</span>}
                            <div className="sd-risk-assignee-main"><div><strong>{person.name}</strong><span>{person.unresolvedTasks} open</span></div><div className="sd-workload-track"><span style={{ width: `${Math.max(4, person.unresolvedTasks * 100 / Math.max(1, ...data.unresolvedByAssignee.map(item => item.unresolvedTasks)))}%` }} /></div></div>
                        </div>)}</div> : <p className="sd-inline-empty">No unresolved tasks in this scope.</p>}
                    </section>
                    <section className="sd-risk-subsection"><div className="sd-risk-subhead"><h3>Active sprint risk</h3><span>Unresolved / total · end date</span></div>
                        {data.sprintRisks?.length ? <div className="sd-risk-sprints">{data.sprintRisks.map(sprint => <button type="button" className="sd-risk-sprint" key={sprint.sprintId} onClick={() => setSprintId(String(sprint.sprintId))}>
                            <span className={`sd-risk-pill sd-risk-${String(sprint.riskLevel).toLowerCase()}`}>{labelStatus(sprint.riskLevel)}</span><strong>{sprint.sprintName}</strong><small>{sprint.projectName || "Project"} · ends {formatDate(sprint.endDate)}</small>
                            <span className="sd-risk-sprint-count">{sprint.unresolvedTasks} unresolved <b>·</b> {sprint.overdueTasks} overdue <b>·</b> {sprint.approachingDeadlineTasks} due soon</span>
                            <span className="sd-risk-progress"><i style={{ width: `${sprint.completionRate}%` }} /></span><small>{sprint.completedTasks}/{sprint.totalTasks} resolved ({sprint.completionRate}%)</small>
                        </button>)}</div> : <p className="sd-inline-empty">No active sprints in this scope.</p>}
                    </section>
                </div>
                <p className="sd-footnote">{data.definitions} Deadline lists show at most 10 tasks each, ordered by nearest due date.</p>
            </div>}
        </SectionState>
        <SectionState state={bottlenecks} empty={!bottlenecks.data?.blockedTasks?.length && !bottlenecks.data?.overdueTasks?.length} emptyText="No blocked or past-sprint-end tasks in this scope.">
            {data => <div className="sd-panel-content"><div className="sd-section-head"><div><p className="sd-kicker">NEEDS ATTENTION</p><h2>Bottlenecks</h2><p>Open blockers and unresolved work whose sprint end date has passed.</p></div></div>
                <div className="sd-bottleneck-grid">
                    <div><h3>Blocked tasks <span>{data.blockedTasks?.length || 0}</span></h3>{data.blockedTasks?.length ? data.blockedTasks.map((task, index) => <button type="button" className="sd-task-link" key={`${task.taskId}-${task.blockingTaskId}-${index}`} onClick={() => openTask(task.taskId)}>
                        <span className="sd-task-id">#{task.taskId}</span><span className="sd-task-copy"><strong>{task.title || `Task #${task.taskId}`}</strong><small>Blocked by <b>#{task.blockingTaskId}</b> · {task.blockingTaskTitle}</small></span><span className="sd-link-arrow" aria-hidden="true">↗</span>
                    </button>) : <p className="sd-inline-empty">No unresolved blockers.</p>}</div>
                    <div><h3>Past sprint end <span>{data.overdueTasks?.length || 0}</span></h3>{data.overdueTasks?.length ? data.overdueTasks.map(task => <button type="button" className="sd-task-link" key={task.taskId} onClick={() => openTask(task.taskId)}>
                        <span className="sd-task-id">#{task.taskId}</span><span className="sd-task-copy"><strong>{task.title || `Task #${task.taskId}`}</strong><small>{task.sprintName} ended {formatDate(task.sprintEndDate)}{task.assigneeName ? ` · ${task.assigneeName}` : ""}</small></span><span className="sd-link-arrow" aria-hidden="true">↗</span>
                    </button>) : <p className="sd-inline-empty">No unresolved tasks in ended sprints.</p>}</div>
                </div>
                <p className="sd-footnote">{data.overdueDefinition}</p>
            </div>}
        </SectionState>
    </main>;
}

function SectionState({ state, empty, emptyText, children }) {
    if (state.disabled) return null;
    return <section className="sd-panel">
        {state.loading ? <div className="sd-state" role="status"><span className="sd-spinner" />Loading this section…</div>
            : state.error ? <div className="sd-state sd-state-error" role="alert"><strong>Could not load this section</strong><span>{state.error}</span></div>
                : empty ? <div className="sd-state sd-state-empty">{emptyText}</div>
                    : state.data == null ? <div className="sd-state sd-state-error" role="alert">No dashboard data was returned for this section.</div>
                        : children(state.data)}
    </section>;
}

function RiskTaskList({ title, tasks = [], tone, onOpen, emptyText }) {
    return <section className={`sd-risk-deadline sd-risk-deadline-${tone}`}><div className="sd-risk-subhead"><h3>{title}</h3><span>{tasks.length}</span></div>
        {tasks.length ? tasks.map(task => <button type="button" className="sd-risk-task" key={task.taskId} onClick={() => onOpen(task.taskId)}>
            <span className="sd-task-id">#{task.taskId}</span><span className="sd-task-copy"><strong>{task.title || `Task #${task.taskId}`}</strong><small>{formatDate(task.dueDate)} · {task.assigneeName || "Unassigned"} · {task.sprintName || task.projectName || "No sprint/project"}</small></span><span className="sd-risk-task-status">{labelStatus(task.status)}</span>
        </button>) : <p className="sd-inline-empty">{emptyText}</p>}
    </section>;
}
function Metric({ label, value, note, tone }) {
    return <article className={`sd-metric sd-metric-${tone}`}><span>{label}</span><strong>{value}</strong><small>{note}</small></article>;
}

function StatusDistribution({ statuses, total }) {
    const nonzero = statuses.filter(item => Number(item.count) > 0);
    return <>
        {total > 0 && <div className="sd-status-bar" role="img" aria-label="Task distribution by status">{nonzero.map(item => <span key={item.status} title={`${labelStatus(item.status)}: ${item.count}`} style={{ width: `${item.count * 100 / total}%`, backgroundColor: statusColors[item.status] || "#7c8aa3" }} />)}</div>}
        <ul className="sd-status-list">{statuses.map(item => <li key={item.status}><span><i style={{ background: statusColors[item.status] || "#7c8aa3" }} />{labelStatus(item.status)}</span><strong>{item.count}<small>{total ? `${Math.round(item.count * 100 / total)}%` : "0%"}</small></strong></li>)}</ul>
    </>;
}

function BurndownChart({ data }) {
    const points = data.snapshots || [];
    const max = Math.max(1, ...points.map(point => Math.max(point.remainingIssues, point.idealRemainingIssues)));
    const width = 680;
    const height = 210;
    const padding = { top: 18, right: 14, bottom: 30, left: 30 };
    const toPoints = key => points.map((point, index) => {
        const x = padding.left + (points.length === 1 ? (width - padding.left - padding.right) / 2 : index * (width - padding.left - padding.right) / (points.length - 1));
        const y = padding.top + (1 - point[key] / max) * (height - padding.top - padding.bottom);
        return `${x},${y}`;
    }).join(" ");
    return <div className="sd-panel-content"><div className="sd-section-head"><div><p className="sd-kicker">RECORDED HISTORY</p><h2>Sprint burndown</h2><p>{data.sprintName} · tracking since {formatDate(data.trackingStartedOn)}</p></div></div>
        <div className="sd-chart-legend"><span><i className="sd-actual-key" />Actual remaining</span><span><i className="sd-ideal-key" />Ideal trend</span></div>
        <div className="sd-chart-wrap"><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`Recorded sprint burndown for ${data.sprintName}`}>
            {[0, 1, 2, 3].map(index => { const y = padding.top + index * (height - padding.top - padding.bottom) / 3; return <g key={index}><line x1={padding.left} x2={width - padding.right} y1={y} y2={y} className="sd-chart-grid" /><text x="0" y={y + 4} className="sd-chart-label">{Math.round(max * (3 - index) / 3)}</text></g>; })}
            {points.length > 1 && <><polyline points={toPoints("idealRemainingIssues")} className="sd-chart-ideal" /><polyline points={toPoints("remainingIssues")} className="sd-chart-actual" /></>}
            {points.map((point, index) => { const x = padding.left + (points.length === 1 ? (width - padding.left - padding.right) / 2 : index * (width - padding.left - padding.right) / (points.length - 1)); const y = padding.top + (1 - point.remainingIssues / max) * (height - padding.top - padding.bottom); return <g key={point.date}><circle cx={x} cy={y} r="3.5" className="sd-chart-dot" /><title>{`${formatDate(point.date)}: ${point.remainingIssues} remaining`}</title></g>; })}
            {points.map((point, index) => <text key={point.date} x={padding.left + (points.length === 1 ? (width - padding.left - padding.right) / 2 : index * (width - padding.left - padding.right) / (points.length - 1))} y={height - 5} textAnchor="middle" className="sd-chart-label">{formatShortDate(point.date)}</text>)}
        </svg></div>
        <p className="sd-footnote">Actual line includes only snapshots recorded by the collector. Sprint dates: {formatDate(data.sprintStartDate)} – {formatDate(data.sprintEndDate)}.</p>
    </div>;
}

function labelStatus(value) { return String(value || "Unknown").toLowerCase().replaceAll("_", " ").replace(/\b\w/g, char => char.toUpperCase()); }
function formatDate(value) { return value ? new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)) : "Date unavailable"; }
function formatWait(seconds) {
    const totalMinutes = Math.max(0, Math.floor(Number(seconds || 0) / 60));
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = totalMinutes % 60;
    if (days) return `${days}d ${hours}h`;
    if (hours) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
}
function formatApprovalDate(value) {
    if (!value) return "date unavailable";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "date unavailable" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}
function formatShortDate(value) { return value ? new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)) : ""; }

export default Dashboard;
