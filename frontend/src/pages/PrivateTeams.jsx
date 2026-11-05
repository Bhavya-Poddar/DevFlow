import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Avatar from "../components/Avatar";
import SearchableMemberSelector from "../components/SearchableMemberSelector";
import { membersForResponsibility } from "../utils/teamMemberEligibility";
import { apiUrl } from "../services/apiConfig";
import "./PrivateTeams.css";

async function request(path, options = {}) {
    const token = localStorage.getItem("token");
    const response = await fetch(apiUrl(path), { ...options, headers: { ...(options.body ? { "Content-Type": "application/json" } : {}), Authorization: `Bearer ${token}`, ...(options.headers || {}) } });
    if (!response.ok) {
        let message = `Request failed (${response.status})`;
        try { message = (await response.json()).message || message; } catch { /* keep the safe status fallback */ }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
}

const nameOf = value => value?.name?.trim() || "Unnamed member";
const label = value => String(value || "Unavailable").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, char => char.toUpperCase());
const dateLabel = value => value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "Time unavailable";

function Modal({ title, onClose, children, wide = false }) {
    useEffect(() => {
        const closeOnEscape = event => { if (event.key === "Escape") onClose(); };
        window.addEventListener("keydown", closeOnEscape);
        return () => window.removeEventListener("keydown", closeOnEscape);
    }, [onClose]);
    return <div className="teams-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
        <section className={`teams-modal ${wide ? "wide" : ""}`} role="dialog" aria-modal="true" aria-labelledby="teams-modal-title">
            <header><div><small>DEVFLOW WORKSPACE</small><h2 id="teams-modal-title">{title}</h2></div><button type="button" aria-label="Close" onClick={onClose}>×</button></header>{children}
        </section>
    </div>;
}

function AvatarStack({ members = [] }) {
    return <div className="teams-avatar-stack" aria-label={`${members.length} members`}>
        {members.slice(0, 5).map(member => <Avatar key={member.id} name={nameOf(member)} src={member.avatarUrl} size={33} className="teams-stack-avatar" />)}
        {members.length > 5 && <span className="teams-stack-more">+{members.length - 5}</span>}
    </div>;
}

function ActivityFeed({ events = [], loading, error, emptyMessage }) {
    if (loading) return <div className="teams-inline-state"><i className="teams-spinner" />Loading activity…</div>;
    if (error) return <div className="teams-error" role="alert">{error}</div>;
    if (!events.length) return <div className="teams-empty compact"><span>◷</span><h3>No activity yet</h3><p>{emptyMessage}</p></div>;
    return <ol className="teams-activity">{events.map(event => <li key={event.id}>
        <Avatar name={event.actorName || "Unavailable member"} src={event.actorAvatarUrl} size={36} />
        <div className="teams-activity-body"><strong>{event.actorName || "Unavailable member"} <em>{label(event.action)}</em></strong>
            <p>{event.details || (event.repository ? `${event.repository} activity` : "No additional details recorded.")}</p>
            <small><time dateTime={event.occurredAt || undefined}>{dateLabel(event.occurredAt)}</time>{event.taskId && <Link to="/issues">{event.taskTitle || `Task #${event.taskId}`}</Link>}{event.repository && <span>{event.repository}</span>}</small>
        </div>
    </li>)}</ol>;
}

function ProjectTile({ project, issues, compact = false }) {
    const tasks = issues.filter(issue => Number(issue.project?.id) === Number(project.id));
    const complete = tasks.filter(issue => ["RESOLVED", "CLOSED"].includes(issue.status)).length;
    const percent = tasks.length ? Math.round(complete / tasks.length * 100) : 0;
    const people = [...new Map(tasks.map(task => task.assignedUser).filter(Boolean).map(user => [user.id, user])).values()];
    return <Link to="/projects" className={`teams-project-tile ${compact ? "compact" : ""}`}>
        <div className="teams-project-line"><span className="teams-project-icon">▧</span><span className="teams-project-state">{project.status || "Project"}</span></div>
        <h3>{project.name || "Untitled project"}</h3><p>{project.description || "No project description provided."}</p>
        <div className="teams-project-metrics"><span><b>{tasks.length}</b> tasks</span><span><b>{percent}%</b> complete</span></div>
        <div className="teams-progress"><i style={{ width: `${percent}%` }} /></div>
        <div className="teams-project-people"><AvatarStack members={people} /><span>{people.length ? `${people.length} assignees` : "No assigned developers"}</span><b>↗</b></div>
    </Link>;
}

export default function PrivateTeams() {
    const [teams, setTeams] = useState([]), [users, setUsers] = useState([]), [projects, setProjects] = useState([]), [issues, setIssues] = useState([]), [me, setMe] = useState(null);
    const [loading, setLoading] = useState(true), [errors, setErrors] = useState({}), [notice, setNotice] = useState(""), [saving, setSaving] = useState(false);
    const [createOpen, setCreateOpen] = useState(false), [newName, setNewName] = useState(""), [newProjectId, setNewProjectId] = useState(""), [teamComposition, setTeamComposition] = useState({ managerUserId: "", projectManagerIds: [], teamLeadIds: [], developerIds: [], testerIds: [] }), [selectedId, setSelectedId] = useState(null), [tab, setTab] = useState("Overview"), [member, setMember] = useState(null), [memberIds, setMemberIds] = useState({}), [memberResponsibilities, setMemberResponsibilities] = useState({}), [search, setSearch] = useState("");
    const [activities, setActivities] = useState({});

    const load = useCallback(async () => {
        setLoading(true); setErrors({});
        const results = await Promise.allSettled([request("/api/teams"), request("/api/users/me"), request("/api/projects"), request("/api/issues"), request("/api/users/assignable")]);
        const next = {};
        const apply = (index, setter, errorKey, transform = value => value) => {
            const result = results[index];
            if (result.status === "fulfilled") setter(transform(result.value));
            else next[errorKey] = result.reason?.message || `Unable to load ${errorKey}.`;
        };
        apply(0, setTeams, "teams", value => Array.isArray(value) ? value : []);
        apply(1, setMe, "profile");
        apply(2, setProjects, "projects", value => Array.isArray(value) ? value : []);
        apply(3, setIssues, "tasks", value => Array.isArray(value) ? value : []);
        apply(4, setUsers, "directory", value => Array.isArray(value) ? value : []);
        const requestedId = new URLSearchParams(window.location.search).get("teamId");
        const teamResult = results[0];
        const loadedTeams = teamResult.status === "fulfilled" && Array.isArray(teamResult.value) ? teamResult.value : [];
        if (requestedId && loadedTeams.some(team => String(team.id) === requestedId)) setSelectedId(requestedId);
        if (requestedId) {
            const nextParams = new URLSearchParams(window.location.search);
            nextParams.delete("teamId");
            const query = nextParams.toString();
            window.history.replaceState(null, "", window.location.pathname + (query ? "?" + query : "") + window.location.hash);
        }
        setErrors(next); setLoading(false);
    }, []);
    useEffect(() => { const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [load]);


    const selected = useMemo(() => teams.find(team => Number(team.id) === Number(selectedId)) || null, [teams, selectedId]);
    const canCreate = ["ADMIN", "PROJECT_MANAGER"].includes(me?.role);
    const canManage = team => Boolean(me && (me.role === "ADMIN" || Number(team?.manager?.id) === Number(me.id)));
    const matches = useMemo(() => teams.filter(team => `${team.name} ${team.manager?.name || ""}`.toLowerCase().includes(search.toLowerCase())), [teams, search]);
    const teamProjects = useMemo(() => selected ? projects.filter(project => Number(project.teamId) === Number(selected.id)) : [], [selected, projects]);
    const projectIds = useMemo(() => new Set(teamProjects.map(project => Number(project.id))), [teamProjects]);
    const teamIssues = useMemo(() => issues.filter(issue => projectIds.has(Number(issue.project?.id))), [issues, projectIds]);
    const availableProjects = useMemo(() => projects.filter(project => (project.teamId ?? project.team?.id) == null), [projects]);
    const activity = selected ? activities[selected.id] : null;

    const loadActivity = useCallback(async teamId => {
        setActivities(current => ({ ...current, [teamId]: { ...current[teamId], loading: true, error: "" } }));
        try { const data = await request(`/api/teams/${teamId}/activity?limit=40`); setActivities(current => ({ ...current, [teamId]: { events: Array.isArray(data) ? data : [], loading: false, error: "" } })); }
        catch (error) { setActivities(current => ({ ...current, [teamId]: { ...current[teamId], loading: false, error: error.message || "Unable to load activity." } })); }
    }, []);
    useEffect(() => {
        if (selected && (tab === "Activity" || member) && !activity?.loading && !activity?.events && !activity?.error) void loadActivity(selected.id);
    }, [selected, tab, member, activity, loadActivity]);

    const mutate = async (operation, success) => {
        setSaving(true); setErrors(current => ({ ...current, action: "" })); setNotice("");
        try { await operation(); setNotice(success); await load(); }
        catch (error) { setErrors(current => ({ ...current, action: error.message || "The change could not be saved." })); }
        finally { setSaving(false); }
    };
    const createTeam = async event => {
        event.preventDefault();
        const selections = [teamComposition.projectManagerIds, teamComposition.teamLeadIds, teamComposition.developerIds, teamComposition.testerIds];
        const people = selections.flat();
        if (selections.some(list => !list.length) || new Set(people).size !== people.length || !teamComposition.projectManagerIds.includes(teamComposition.managerUserId)) {
            setErrors(current => ({ ...current, action: "Choose at least one person for each responsibility, select the designated manager from the project managers, and assign each person one responsibility." }));
            return;
        }
        await mutate(async () => { const created = await request("/api/teams", { method: "POST", body: JSON.stringify({ name: newName.trim(), projectId: Number(newProjectId), composition: { managerUserId: Number(teamComposition.managerUserId), projectManagerIds: teamComposition.projectManagerIds.map(Number), teamLeadIds: teamComposition.teamLeadIds.map(Number), developerIds: teamComposition.developerIds.map(Number), testerIds: teamComposition.testerIds.map(Number) } }) }); setNewName(""); setNewProjectId(""); setTeamComposition({ managerUserId: "", projectManagerIds: [], teamLeadIds: [], developerIds: [], testerIds: [] }); setCreateOpen(false); setSelectedId(created.id); setTab("Overview"); }, "Team created and linked to the selected project.");
    };
    const addMember = async event => {
        event.preventDefault(); const developerId = memberIds[selected.id], responsibility = memberResponsibilities[selected.id]; if (!developerId || !responsibility) return;
        await mutate(async () => { await request(`/api/teams/${selected.id}/members`, { method: "POST", body: JSON.stringify({ developerId: Number(developerId), responsibility }) }); setMemberIds(current => ({ ...current, [selected.id]: "" })); }, "Member added with the selected project responsibility.");
    };
    const removeMember = person => { setMember(null); void mutate(() => request(`/api/teams/${selected.id}/members/${person.id}`, { method: "DELETE" }), `${nameOf(person)} was removed from the team.`); };

    if (loading && !teams.length && !selectedId) return <main className="teams-page"><div className="teams-loading"><i className="teams-spinner" />Loading your teams…</div></main>;
    if (selectedId && !selected && errors.teams) return <main className="teams-page"><div className="teams-error" role="alert">{errors.teams} <button type="button" onClick={() => void load()}>Try again</button></div></main>;

    if (selected) return <main className="teams-page">
        <div className="teams-topline"><button className="teams-back" type="button" onClick={() => { setSelectedId(null); setMember(null); }}>← <span>All teams</span></button><button className="teams-refresh" type="button" onClick={() => { void load(); if (tab === "Activity") void loadActivity(selected.id); }}>↻ Refresh</button></div>
        <header className="teams-detail-hero"><span className="teams-detail-mark">{selected.name.slice(0, 1).toUpperCase()}</span><div><div className="teams-detail-title"><h1>{selected.name}</h1><span className="teams-private">⌑ Private workspace</span>{canManage(selected) && <span className="teams-manager-tag">Manager</span>}</div><p>Managed by {nameOf(selected.manager)} · {selected.members?.length || 0} people · {errors.projects ? "projects unavailable" : `${teamProjects.length} projects`}</p></div></header>
        {errors.action && <div className="teams-banner error" role="alert">{errors.action}</div>}{notice && <div className="teams-banner success" role="status">{notice}</div>}
        <nav className="teams-tabs" aria-label="Team sections">{["Overview", "Members", "Projects", "Activity"].map(name => <button type="button" key={name} className={tab === name ? "active" : ""} onClick={() => { setTab(name); setMember(null); }}>{name}{name === "Members" && <i>{selected.members?.length || 0}</i>}{name === "Projects" && <i>{teamProjects.length}</i>}</button>)}</nav>
        {tab === "Overview" && <div className="teams-layout"><section className="teams-column"><div className="teams-section-title"><small>TEAM SNAPSHOT</small><h2>Workspace overview</h2></div><div className="teams-stats"><article><i className="blue">♙</i><strong>{selected.members?.length || 0}</strong><span>Team members</span></article><article><i className="violet">▧</i><strong>{errors.projects ? "—" : teamProjects.length}</strong><span>{errors.projects ? "Projects unavailable" : "Private projects"}</span></article><article><i className="teal">◈</i><strong>{errors.tasks ? "—" : teamIssues.length}</strong><span>{errors.tasks ? "Tasks unavailable" : "Project tasks"}</span></article></div>
            <section className="teams-panel"><header><div><small>PEOPLE</small><h3>Team members</h3></div><button type="button" onClick={() => setTab("Members")}>See all</button></header>{selected.members?.length ? <div className="teams-member-grid">{selected.members.slice(0, 6).map(person => <button key={person.id} type="button" onClick={() => setMember(person)}><Avatar name={nameOf(person)} src={person.avatarUrl} size={43} /><span><b>{nameOf(person)}</b><small>{label(person.role)}</small></span><em>↗</em></button>)}</div> : <p className="teams-muted">No member profiles are available.</p>}</section>
            <section className="teams-panel"><header><div><small>PROJECT SPACE</small><h3>Projects</h3></div><button type="button" onClick={() => setTab("Projects")}>See all</button></header>{errors.projects ? <div className="teams-error">{errors.projects}</div> : teamProjects.length ? teamProjects.slice(0, 3).map(project => <ProjectSummary key={project.id} project={project} issues={teamIssues} />) : <p className="teams-muted">No projects are assigned to this team yet.</p>}</section>
        </section><aside className="teams-column"><section className="teams-panel"><small>TEAM MANAGER</small><button className="teams-manager-person" type="button" onClick={() => selected.manager && setMember(selected.manager)}><Avatar name={nameOf(selected.manager)} src={selected.manager?.avatarUrl} size={48} /><span><b>{nameOf(selected.manager)}</b><small>{label(selected.manager?.role)}</small></span><em>↗</em></button><div className="teams-privacy-note">⌑ <span>Only this team and administrators can access its assigned projects.</span></div></section>
            <section className="teams-panel"><header><div><small>RECENT</small><h3>Activity</h3></div><button type="button" onClick={() => setTab("Activity")}>View feed</button></header>{activity ? <ActivityFeed events={(activity.events || []).slice(0, 4)} loading={activity.loading} error={activity.error} emptyMessage="Task activity will appear when recorded." /> : <button className="teams-load-activity" type="button" onClick={() => void loadActivity(selected.id)}>Load recorded activity</button>}</section></aside></div>}
        {tab === "Members" && <section className="teams-panel teams-tab-panel"><header className="teams-tab-heading"><div><small>YOUR COLLABORATORS</small><h2>Members <i>{selected.members?.length || 0}</i></h2><p>People who can access this team’s private projects.</p></div></header>
            {errors.directory && canManage(selected) && <div className="teams-error" role="alert">{errors.directory}</div>}{canManage(selected) && <form className="teams-add-member" onSubmit={addMember}><label htmlFor="add-team-member">Add a project team member</label><div><select id="add-team-member" value={memberIds[selected.id] || ""} onChange={event => setMemberIds(current => ({ ...current, [selected.id]: event.target.value }))}><option value="">Choose an enabled registered person</option>{users.filter(person => !(selected.members || []).some(existing => Number(existing.id) === Number(person.id))).map(person => <option key={person.id} value={person.id}>{nameOf(person)} · {label(person.role)}</option>)}</select><select aria-label="Project responsibility" value={memberResponsibilities[selected.id] || ""} onChange={event => setMemberResponsibilities(current => ({ ...current, [selected.id]: event.target.value }))}><option value="">Choose responsibility</option><option value="PROJECT_MANAGER_TEAM">Project Manager</option><option value="TEAM_LEAD_TEAM">Team Lead</option><option value="DEVELOPER_TEAM">Developer</option><option value="TESTER_TEAM">Tester</option></select><button disabled={saving || !memberIds[selected.id] || !memberResponsibilities[selected.id]}>{saving ? "Adding…" : "Add member"}</button></div><small>One responsibility per person is supported by the current role-assignment model.</small></form>}
            {selected.members?.length ? <div className="teams-people-list">{selected.members.map(person => <article key={person.id}><button type="button" className="teams-people-profile" onClick={() => setMember(person)}><Avatar name={nameOf(person)} src={person.avatarUrl} size={48} /><span><b>{nameOf(person)}</b><small>{label(person.role)}</small></span></button><span className="teams-membership-pill">{Number(person.id) === Number(selected.manager?.id) ? "Team manager" : "Member"}</span>{canManage(selected) && Number(person.id) !== Number(selected.manager?.id) && <button className="teams-remove" type="button" disabled={saving} onClick={() => removeMember(person)}>Remove</button>}</article>)}</div> : <Empty title="No members yet" message="Add a registered developer or tester to collaborate on private team projects." />}</section>}
        {tab === "Projects" && <section className="teams-panel teams-tab-panel"><header className="teams-tab-heading"><div><small>PRIVATE WORKSPACE</small><h2>Projects <i>{teamProjects.length}</i></h2><p>Project and task summaries use the currently available authenticated APIs.</p></div></header>{errors.projects ? <div className="teams-error" role="alert">{errors.projects}</div> : teamProjects.length ? <div className="teams-project-grid">{teamProjects.map(project => <ProjectTile key={project.id} project={project} issues={teamIssues} />)}</div> : <Empty title="No team projects yet" message="Assign a project to this team from the existing Projects page."><Link className="teams-primary-link" to="/projects">Open projects ↗</Link></Empty>}{errors.tasks && <div className="teams-error">Task counts are unavailable: {errors.tasks}</div>}</section>}
        {tab === "Activity" && <section className="teams-panel teams-tab-panel"><header className="teams-tab-heading"><div><small>RECORDED HISTORY</small><h2>Team activity</h2><p>Task audit activity from this team’s projects. Team membership changes are not currently recorded.</p></div><button type="button" onClick={() => void loadActivity(selected.id)}>Refresh feed</button></header><ActivityFeed events={activity?.events || []} loading={!activity || activity.loading} error={activity?.error} emptyMessage="No task audit activity has been recorded for these projects." /></section>}
        {member && <Modal title="Member profile" wide onClose={() => setMember(null)}><MemberProfile person={member} team={selected} tasks={teamIssues} activity={activity} loadingTasks={Boolean(errors.tasks)} onOpenTask={() => setMember(null)} /></Modal>}
    </main>;

    return <main className="teams-page">
        <header className="teams-index-hero"><div><small className="teams-kicker">DEVFLOW · COLLABORATION</small><h1>Your teams</h1><p>Private spaces for people building together. Team membership controls access to assigned projects.</p><div className="teams-hero-metrics"><span><b>{teams.length}</b> teams</span><i /><span><b>{errors.projects ? "—" : projects.filter(project => project.teamId != null).length}</b> private projects visible</span></div></div><div className="teams-hero-actions">{canCreate && <button className="teams-create" type="button" onClick={() => setCreateOpen(true)}>＋ <span>Create team</span></button>}<button className="teams-refresh" type="button" onClick={() => void load()}>↻ <span>Refresh</span></button></div></header>
        {errors.action && <div className="teams-banner error" role="alert">{errors.action}</div>}{notice && <div className="teams-banner success" role="status">{notice}</div>}
        <div className="teams-index-toolbar"><div><small>WORKSPACES</small><h2>Teams <i>{teams.length}</i></h2></div><label className="teams-search"><span>⌕</span><input aria-label="Search teams" placeholder="Find a team…" value={search} onChange={event => setSearch(event.target.value)} /></label></div>
        {errors.teams && <div className="teams-load-error" role="alert"><div><b>Teams could not be loaded</b><p>{errors.teams}</p></div><button type="button" onClick={() => void load()}>Try again</button></div>}{errors.projects && <div className="teams-note" role="status">Project summaries are unavailable. Team and membership details remain available.</div>}
        {loading && !teams.length ? <div className="teams-loading"><i className="teams-spinner" />Loading teams…</div> : !errors.teams && !matches.length ? <Empty title={search ? "No matching teams" : "Your collaboration starts here"} message={search ? "Try another name or clear the search." : canCreate ? "Create a private team, invite developers and testers, and give them a focused project space." : "You do not belong to a team yet. Ask a project manager to add you to a workspace."}>{search ? <button type="button" onClick={() => setSearch("")}>Clear search</button> : canCreate && <button type="button" className="teams-create" onClick={() => setCreateOpen(true)}>Create your first team</button>}</Empty> : <div className="teams-card-grid">{matches.map(team => {
            const teamProjectsForCard = projects.filter(project => Number(project.teamId) === Number(team.id));
            return <button key={team.id} type="button" className="teams-card" onClick={() => { setSelectedId(team.id); setTab("Overview"); }}><div className="teams-card-top"><span className="teams-card-mark">{team.name.slice(0, 1).toUpperCase()}</span><span className="teams-private">⌑ Private</span><span className="teams-card-open">↗</span></div><h3>{team.name}</h3><p>Managed by {nameOf(team.manager)}</p><div className="teams-card-members"><AvatarStack members={team.members || []} /><span>{team.members?.length || 0} members</span></div><footer><span><b>{errors.projects ? "—" : teamProjectsForCard.length}</b> {errors.projects ? "projects unavailable" : teamProjectsForCard.length === 1 ? "project" : "projects"}</span><span>{canManage(team) ? "You manage this team" : "Open workspace"} <b>→</b></span></footer></button>;
        })}</div>}
        {errors.directory && canCreate && <p className="teams-muted">Member directory unavailable: {errors.directory}</p>}
        {createOpen && <Modal title="Create a project team" wide onClose={() => !saving && setCreateOpen(false)}><form className="teams-create-form" onSubmit={createTeam}>
            <p>Assign each minimum project responsibility to a different registered member. The designated manager must be one of the Project Managers.</p>
            <label htmlFor="new-team-name">Team name</label><input id="new-team-name" required maxLength={120} placeholder="e.g. Platform Engineering" value={newName} onChange={event => setNewName(event.target.value)} />
            <SearchableMemberSelector id="team-project-managers" label="Project Managers" people={membersForResponsibility(users, "PROJECT_MANAGER")} emptyMessage="No enabled Project Manager accounts are available." selected={teamComposition.projectManagerIds} excluded={[...teamComposition.teamLeadIds, ...teamComposition.developerIds, ...teamComposition.testerIds]} onChange={projectManagerIds => setTeamComposition(current => ({ ...current, projectManagerIds, managerUserId: projectManagerIds.includes(current.managerUserId) ? current.managerUserId : "" }))} getMeta={person => label(person.role)} />
            <label htmlFor="team-manager">Designated Project Manager <small className="ml-1 font-normal text-gray-400">Only selected Project Managers are available</small></label>
            <select id="team-manager" required value={teamComposition.managerUserId} onChange={event => setTeamComposition(current => ({ ...current, managerUserId: event.target.value }))}><option value="">Select from the chosen Project Managers</option>{users.filter(person => teamComposition.projectManagerIds.includes(String(person.id))).map(person => <option key={person.id} value={String(person.id)}>{nameOf(person)}</option>)}</select>
            <SearchableMemberSelector id="team-leads" label="Team Leads" people={membersForResponsibility(users, "TEAM_LEAD")} emptyMessage="No enabled Team Lead accounts are available." selected={teamComposition.teamLeadIds} excluded={[...teamComposition.projectManagerIds, ...teamComposition.developerIds, ...teamComposition.testerIds]} onChange={teamLeadIds => setTeamComposition(current => ({ ...current, teamLeadIds }))} getMeta={person => label(person.role)} />
            <SearchableMemberSelector id="team-developers" label="Developers" people={membersForResponsibility(users, "DEVELOPER")} emptyMessage="No enabled Developer accounts are available." selected={teamComposition.developerIds} excluded={[...teamComposition.projectManagerIds, ...teamComposition.teamLeadIds, ...teamComposition.testerIds]} onChange={developerIds => setTeamComposition(current => ({ ...current, developerIds }))} getMeta={person => label(person.role)} />
            <SearchableMemberSelector id="team-testers" label="Testers" people={membersForResponsibility(users, "TESTER")} emptyMessage="No enabled Tester accounts are available." selected={teamComposition.testerIds} excluded={[...teamComposition.projectManagerIds, ...teamComposition.teamLeadIds, ...teamComposition.developerIds]} onChange={testerIds => setTeamComposition(current => ({ ...current, testerIds }))} getMeta={person => label(person.role)} />
            <label htmlFor="new-team-project">Project</label><select id="new-team-project" required value={newProjectId} onChange={event => setNewProjectId(event.target.value)}><option value="">Select an unassigned project</option>{availableProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select>
            {errors.directory && <div className="teams-error" role="alert">Could not load eligible people: {errors.directory}</div>}{errors.action && <div className="teams-error" role="alert">{errors.action}</div>}
            <footer><button type="button" onClick={() => setCreateOpen(false)} disabled={saving}>Cancel</button><button className="teams-create" disabled={saving || !newName.trim() || !newProjectId || !teamComposition.managerUserId}>{saving ? "Creating…" : "Create project team"}</button></footer>
        </form></Modal>}
    </main>;
}

function Empty({ title, message, children }) { return <div className="teams-empty"><span>◈</span><h3>{title}</h3><p>{message}</p>{children}</div>; }


function ProjectSummary({ project, issues }) {
    const tasks = issues.filter(task => Number(task.project?.id) === Number(project.id));
    const done = tasks.filter(task => ["RESOLVED", "CLOSED"].includes(task.status)).length;
    const percent = tasks.length ? Math.round(done / tasks.length * 100) : 0;
    return <Link to="/projects" className="teams-project-summary"><span className="teams-project-icon">▧</span><span><b>{project.name || "Untitled project"}</b><small>{project.status || "Status unavailable"} · {tasks.length} tasks</small></span><i><em style={{ width: `${percent}%` }} /></i><strong>{percent}%</strong></Link>;
}

function MemberProfile({ person, team, tasks, activity, loadingTasks, onOpenTask }) {
    const assigned = tasks.filter(task => Number(task.assignedUser?.id) === Number(person.id) || Number(task.tester?.id) === Number(person.id));
    const projectCount = new Set(assigned.map(task => Number(task.project?.id))).size;
    const events = (activity?.events || []).filter(event => Number(event.actorId) === Number(person.id));
    return <div className="teams-profile-layout"><div><div className="teams-profile-header"><Avatar name={nameOf(person)} src={person.avatarUrl} size={66} /><span><h3>{nameOf(person)}</h3><p>{label(person.role)}</p><small>{Number(person.id) === Number(team.manager?.id) ? "Team manager" : "Team member"}</small></span></div><div className="teams-profile-stats"><div><b>{assigned.length}</b><span>Assigned tasks</span></div><div><b>{projectCount}</b><span>Projects</span></div><div><b>{events.length}</b><span>Recent audit events</span></div></div><section className="teams-profile-section"><small>ASSIGNMENTS</small><h4>Projects & tasks</h4>{loadingTasks ? <p className="teams-muted">Task assignments could not be loaded.</p> : assigned.length ? <ul>{assigned.slice(0, 8).map(task => <li key={task.id}><span><b>{task.title || `Task #${task.id}`}</b><small>{task.project?.name || "Project unavailable"} · {task.status || "Status unavailable"}{Number(task.tester?.id) === Number(person.id) && Number(task.assignedUser?.id) !== Number(person.id) ? " · Tester" : ""}</small></span><Link to="/issues" onClick={onOpenTask}>View</Link></li>)}</ul> : <p className="teams-muted">No assigned tasks are present in the currently accessible task data.</p>}</section><section className="teams-profile-section"><small>RECENT HISTORY</small><h4>Activity by this member</h4>{!activity || activity.loading ? <p className="teams-muted">Loading recorded activity…</p> : activity.error ? <p className="teams-error">{activity.error}</p> : events.length ? <ul>{events.slice(0, 5).map(event => <li key={event.id}><span><b>{label(event.action)}</b><small>{event.taskTitle || `Task #${event.taskId}`}</small></span><time>{dateLabel(event.occurredAt)}</time></li>)}</ul> : <p className="teams-muted">No recent audit events by this member were returned.</p>}</section></div><aside><small>PROFILE DETAILS</small><dl><dt>Role</dt><dd>{label(person.role)}</dd><dt>Team</dt><dd>{team.name}</dd><dt>Photo</dt><dd>{person.avatarUrl ? "Profile photo available" : "No photo; initials fallback shown"}</dd></dl><p>Contact details are omitted. Profile and assignment information comes from existing authenticated APIs.</p></aside></div>;
}
