import { apiUrl } from "../services/apiConfig";
import { useEffect, useState } from "react";
import RecordTable from "../components/RecordTable";
import SearchableMemberSelector from "../components/SearchableMemberSelector";
import { membersForResponsibility } from "../utils/teamMemberEligibility";
import { loadDropdownOptions, dropdownLabel } from "../services/dropdownOptions";

function Project() {
    const [projects, setProjects] = useState([]);
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [category, setCategory] = useState("");
    const [categoryOptions, setCategoryOptions] = useState([]);
    const [statusOptions, setStatusOptions] = useState([]);
    const [status, setStatus] = useState("ACTIVE");
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
    const [teams, setTeams] = useState([]);
    const [assignableUsers, setAssignableUsers] = useState([]);
    const [teamName, setTeamName] = useState("");
    const [managerUserId, setManagerUserId] = useState("");
    const [projectManagerIds, setProjectManagerIds] = useState([]);
    const [teamLeadIds, setTeamLeadIds] = useState([]);
    const [developerIds, setDeveloperIds] = useState([]);
    const [testerIds, setTesterIds] = useState([]);
    const [selectedTeamId, setSelectedTeamId] = useState("");
    const [categoryOptionsError, setCategoryOptionsError] = useState("");
    const [showForm, setShowForm] = useState(false);
    const [editingProject, setEditingProject] = useState(null);
    const [selectedProject, setSelectedProject] = useState(null);
    const [currentUser, setCurrentUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [creating, setCreating] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const canManage = ["ADMIN", "PROJECT_MANAGER"].includes(currentUser?.role);

    const fetchProjects = async () => {
        const token = localStorage.getItem("token");

        try {
            const [response, userResponse] = await Promise.all([fetch(
                apiUrl("/api/projects"),
                {
                    method: "GET",
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            ), fetch(apiUrl("/api/users/me"), { headers: { Authorization: `Bearer ${token}` } })]);

            if (!response.ok) {
                throw new Error("Failed to fetch projects");
            }

            const data = await response.json();
            const loadedProjects = Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : [];
            setProjects(loadedProjects);
            const requestedId = new URLSearchParams(window.location.search).get("projectId");
            if (requestedId) {
                const project = loadedProjects.find(item => String(item.id) === requestedId);
                if (project) setSelectedProject(project);
                const next = new URLSearchParams(window.location.search);
                next.delete("projectId");
                const query = next.toString();
                window.history.replaceState(null, "", window.location.pathname + (query ? "?" + query : "") + window.location.hash);
            }
            if (userResponse.ok) setCurrentUser(await userResponse.json());
        } catch (error) {
            setError(error.message);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        const token = localStorage.getItem("token");
        loadDropdownOptions("PROJECT_STATUS").then(setStatusOptions).catch(() => setStatusOptions([]));
        fetch(apiUrl("/api/config/dropdowns/PROJECT_CATEGORY"), { headers: { Authorization: "Bearer " + token } })
            .then(async response => { if (!response.ok) throw new Error("Could not load project categories."); return response.json(); })
            .then(data => setCategoryOptions(Array.isArray(data) ? data : []))
            .catch(error => setCategoryOptionsError(error.message));
        fetch(apiUrl("/api/teams"), { headers: { Authorization: "Bearer " + token } })
            .then(response => response.ok ? response.json() : [])
            .then(data => setTeams(Array.isArray(data) ? data : []))
            .catch(() => setTeams([]));
        fetch(apiUrl("/api/users/assignable"), { headers: { Authorization: "Bearer " + token } })
            .then(response => response.ok ? response.json() : [])
            .then(data => setAssignableUsers(Array.isArray(data) ? data : []))
            .catch(() => setAssignableUsers([]));
    }, []);

    useEffect(() => {
        void Promise.resolve().then(fetchProjects);
    }, []);



    const openCreateForm = () => {
        setEditingProject(null);
        setName("");
        setDescription("");
        setCategory("");
        setStatus("ACTIVE");
        setStartDate("");
        setEndDate("");
        setSelectedTeamId("");
        setTeamName(""); setManagerUserId(""); setProjectManagerIds([]); setTeamLeadIds([]); setDeveloperIds([]); setTesterIds([]);
        setError("");
        setNotice("");
        setShowForm(true);
    };

    const openEditForm = (project) => {
        setEditingProject(project);
        setName(project.name);
        setDescription(project.description);
        setCategory(project.category || "");
        setStatus(project.status);
        setStartDate(project.startDate || "");
        setEndDate(project.endDate || "");
        setSelectedTeamId(project.teamId ? String(project.teamId) : "");
        setError("");
        setShowForm(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!editingProject) {
            const requiredSelections = [projectManagerIds, teamLeadIds, developerIds, testerIds];
            const selectedPeople = requiredSelections.flat();
            if (requiredSelections.some(selection => selection.length === 0)) {
                setError("Select at least one Project Manager, Team Lead, Developer, and Tester.");
                return;
            }
            if (new Set(selectedPeople).size !== selectedPeople.length) {
                setError("Each person can have only one responsibility on this project team.");
                return;
            }
            if (!projectManagerIds.includes(managerUserId)) {
                setError("Choose the designated manager from the selected Project Managers.");
                return;
            }
        }

        if (startDate && endDate && endDate < startDate) {
            setError("End date cannot be before start date");
            return;
        }

        setError("");
        setCreating(true);

        const token = localStorage.getItem("token");

        try {
            const url = editingProject
                ? apiUrl(`/api/projects/${editingProject.id}`)
                : apiUrl("/api/projects");

            const method = editingProject ? "PUT" : "POST";

            const payload = {
                    name: name,
                    description: description,
                    category: category || null,
                    status: status,
                    startDate: startDate,
                    endDate: endDate,
                };
            if (editingProject) {
                if (selectedTeamId) payload.teamId = Number(selectedTeamId);
            } else {
                payload.teamName = teamName.trim() || `${name.trim()} Team`;
                payload.teamComposition = {
                    managerUserId: Number(managerUserId),
                    projectManagerIds: projectManagerIds.map(Number),
                    teamLeadIds: teamLeadIds.map(Number),
                    developerIds: developerIds.map(Number),
                    testerIds: testerIds.map(Number)
                };
            }
            const response = await fetch(url, {
                method: method,
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw new Error(await responseError(response, editingProject ? "Failed to update project." : "Failed to create project."));
            }

            setName("");
            setDescription("");
            setCategory("");
            setStatus("ACTIVE");
            setStartDate("");
            setEndDate("");
        setSelectedTeamId("");
            setShowForm(false);
            setEditingProject(null);

            await fetchProjects();
            setNotice(editingProject ? "Project updated successfully." : "Project created successfully.");
        } catch (error) {
            setError(error.message);
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm("Are you sure you want to delete this project?")) return;

        setError("");
        setNotice("");
        setDeleting(true);

        const token = localStorage.getItem("token");

        try {
            const response = await fetch(
                apiUrl(`/api/projects/${id}`),
                {
                    method: "DELETE",
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            );

            if (!response.ok) throw new Error(await responseError(response, "Failed to delete project."));

            setSelectedProject(null);
            await fetchProjects();
            setNotice("Project deleted successfully.");
        } catch (error) {
            setError(error.message);
        } finally {
            setDeleting(false);
        }
    };

    return (
        <main className="dfx-screen dfx-screen--projects">
            <div className="dfx-screen-header">
                <div>
                    <p className="mb-2 text-xs font-semibold uppercase tracking-[.16em] text-blue-300">PORTFOLIO / PROJECTS</p><h1 className="text-3xl font-bold">
                        Projects
                    </h1>

                    <p className="text-gray-400 mt-2">
                        Manage your engineering projects.
                    </p>
                </div>

                {canManage && <button
                    onClick={openCreateForm}
                    className="bg-blue-600 hover:bg-blue-700 px-5 py-3 rounded-lg font-medium transition"
                >
                    + Create Project
                </button>}
            </div>

            <div className="dfx-project-overview" aria-label="Project overview"><article><span>Projects</span><strong>{projects.length}</strong><small>In your workspace</small></article><article><span>Active</span><strong>{projects.filter(project => project.status === "ACTIVE").length}</strong><small>Currently underway</small></article><article><span>Completed</span><strong>{projects.filter(project => project.status === "COMPLETED").length}</strong><small>Marked complete</small></article></div>

            {error && (
                <div className="mb-6 bg-red-500/10 border border-red-500/20 text-red-400 p-4 rounded-lg">
                    {error}
                </div>
            )}
            {notice && <div role="status" className="mb-6 rounded-lg border border-emerald-500/20 bg-emerald-500/10 p-4 text-emerald-300">{notice}</div>}

            {loading ? (
                <p className="text-gray-400">
                    Loading projects...
                </p>
            ) : projects.length === 0 ? (
                <div className="bg-gray-900 border border-gray-800 rounded-xl p-10 text-center">
                    <h2 className="text-xl font-semibold">
                        No projects yet
                    </h2>

                    <p className="text-gray-500 mt-2">
                        Create your first project to get started.
                    </p>
                </div>
            ) : (
                <div className="df-panel overflow-hidden">
                    <RecordTable
                        rows={projects}
                        searchLabel="Search projects"
                        searchPlaceholder="Search projects by name, status, or description…"
                        emptyLabel="No projects match your search."
                        getSearchText={project => `${project.name || ""} ${project.status || ""} ${project.description || ""}`}
                        columns={[
                            { key: "name", label: "Project", sortValue: project => project.name, render: project => <div className="min-w-48"><button type="button" onClick={() => setSelectedProject(project)} className="font-semibold text-white hover:text-blue-300">{project.name || "Untitled project"}</button><p className="mt-1 line-clamp-1 max-w-sm text-xs text-gray-500">{project.description || "No description"}</p></div> },
                            { key: "status", label: "Status", sortValue: project => project.status, render: project => <span className="rounded-full bg-blue-500/10 px-2.5 py-1 text-xs text-blue-300">{dropdownLabel(statusOptions, project.status, project.status?.replaceAll("_", " ") || "Unknown")}</span> },
                            { key: "team", label: "Access team", sortValue: project => project.teamName || "", render: project => project.teamName || "Shared project" },
                            { key: "start", label: "Start date", sortValue: project => project.startDate || "", render: project => project.startDate || "—" },
                            { key: "end", label: "Deadline", sortValue: project => project.endDate || "", render: project => project.endDate || "—" },
                        ]}
                        actionsForRow={project => [
                            { label: "View details", onSelect: () => setSelectedProject(project) },
                            ...(canManage ? [
                                { label: "Edit project", onSelect: () => openEditForm(project) },
                                { label: deleting ? "Deleting…" : "Delete project", danger: true, disabled: deleting, onSelect: () => handleDelete(project.id) }
                            ] : [])
                        ]}
                    />
                </div>
            )}

            {showForm && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center px-4 z-50">
                    <div className="w-full max-w-3xl max-h-[90vh] overflow-y-auto bg-gray-900 border border-gray-800 rounded-2xl p-6 sm:p-8">
                        <div className="flex items-center justify-between mb-6">
                            <h2 className="text-2xl font-semibold">
                                {editingProject
                                    ? "Edit Project"
                                    : "Create Project"}
                            </h2>

                            <button
                                onClick={() => setShowForm(false)}
                                className="text-gray-400 hover:text-white text-xl"
                            >
                                ✕
                            </button>
                        </div>

                        <form
                            onSubmit={handleSubmit}
                            className="space-y-5"
                        >
                            <div className="grid gap-4 sm:grid-cols-2">
                                <label className="block text-sm font-medium text-gray-300">Project status
                                    <select value={status} onChange={event => setStatus(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white">
                                        {status && !statusOptions.some(option => option.code === status) && <option value={status}>{status.replaceAll("_", " ")}</option>}
                                        {(statusOptions.length ? statusOptions : ["ACTIVE", "IN_PROGRESS", "COMPLETED"].map(code => ({code, displayLabel: code.replaceAll("_", " ")}))).map(option => <option key={option.code} value={option.code}>{option.displayLabel}</option>)}
                                    </select>
                                </label>
                                <label className="block text-sm font-medium text-gray-300">Project category (optional)
                                    <select value={category} onChange={event => setCategory(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white">
                                        <option value="">No category</option>{category && !categoryOptions.some(option => option.code === category) && <option value={category} disabled>{category.replaceAll("_", " ")} (disabled existing value)</option>}{categoryOptions.map(option => <option key={option.code} value={option.code}>{option.displayLabel}</option>)}
                                    </select><small className="mt-1 block text-xs text-gray-500">{categoryOptionsError || "Optional business classification."}</small>
                                </label>
                            </div>
                            {!editingProject ? <section className="rounded-xl border border-blue-500/20 bg-blue-500/5 p-4">
                                <h3 className="font-semibold text-white">Project team setup</h3>
                                <p className="mt-1 text-xs text-gray-400">A project team is created with the project. Each person can hold one team responsibility; the designated manager must be one of the selected project managers.</p>
                                <label className="mt-4 block text-sm text-gray-300">Team name<input required maxLength={120} value={teamName} onChange={event => setTeamName(event.target.value)} placeholder={`${name || "Project"} Team`} className="mt-1 w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-white" /></label>
                                <div className="mt-4 grid w-full min-w-0 grid-cols-1 gap-4">
                                    <SearchableMemberSelector id="project-managers" label="Project Managers" people={membersForResponsibility(assignableUsers, "PROJECT_MANAGER")} emptyMessage="No enabled Project Manager accounts are available." selected={projectManagerIds} excluded={[...teamLeadIds, ...developerIds, ...testerIds]} onChange={ids => { setProjectManagerIds(ids); if (!ids.includes(managerUserId)) setManagerUserId(""); }} getMeta={person => person.role ? person.role.replaceAll("_", " ") : "Registered member"} />
                                    <label className="block w-full min-w-0 rounded-xl border border-gray-700/80 bg-gray-900/60 p-4 text-sm font-medium text-gray-200 sm:p-5">Designated Project Manager <span className="ml-2 text-xs font-normal text-gray-400">Choose from the selected Project Managers</span>
                                        <select required value={managerUserId} onChange={event => setManagerUserId(event.target.value)} className="mt-3 min-h-11 w-full rounded-lg border border-gray-700 bg-gray-950 px-3.5 py-2.5 text-sm text-white"><option value="">Choose a selected Project Manager</option>{assignableUsers.filter(person => projectManagerIds.includes(String(person.id))).map(person => <option key={person.id} value={String(person.id)}>{person.name}</option>)}</select>
                                    </label>
                                    <SearchableMemberSelector id="project-team-leads" label="Team Leads" people={membersForResponsibility(assignableUsers, "TEAM_LEAD")} emptyMessage="No enabled Team Lead accounts are available." selected={teamLeadIds} excluded={projectManagerIds} onChange={setTeamLeadIds} getMeta={person => person.role ? person.role.replaceAll("_", " ") : "Registered member"} />
                                    <SearchableMemberSelector id="project-developers" label="Developers" people={membersForResponsibility(assignableUsers, "DEVELOPER")} emptyMessage="No enabled Developer accounts are available." selected={developerIds} excluded={[...projectManagerIds, ...teamLeadIds]} onChange={setDeveloperIds} getMeta={person => [person.role?.replaceAll("_", " "), person.developer && "Developer eligible"].filter(Boolean).join(" · ") || "Registered member"} />
                                    <SearchableMemberSelector id="project-testers" label="Testers" people={membersForResponsibility(assignableUsers, "TESTER")} emptyMessage="No enabled Tester accounts are available." selected={testerIds} excluded={[...projectManagerIds, ...teamLeadIds, ...developerIds]} onChange={setTesterIds} getMeta={person => [person.role?.replaceAll("_", " "), person.tester && "Tester eligible"].filter(Boolean).join(" · ") || "Registered member"} />
                                </div>
                                {!assignableUsers.length && <p className="mt-2 text-sm text-amber-300">The user directory is unavailable. Refresh before creating a project team.</p>}
                            </section> : <label className="block text-sm font-medium text-gray-300">Project team
                                <select value={selectedTeamId} onChange={event=>setSelectedTeamId(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white"><option value="">Keep this project unassociated (legacy only)</option>{teams.filter(team=>!team.projectId||Number(team.id)===Number(selectedTeamId)).map(team=><option key={team.id} value={team.id}>{team.name}</option>)}</select>
                                <small className="mt-1 block text-xs text-gray-500">Existing project/team associations cannot be replaced. A legacy unassociated project may be explicitly mapped to a complete existing team.</small>
                            </label>}
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    Project Name
                                </label>

                                <input
                                    type="text"
                                    value={name}
                                    onChange={(e) =>
                                        setName(e.target.value)
                                    }
                                    placeholder="Enter project name"
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    required
                                />
                            </div>

                            {/* selectors are grouped above to keep the form predictable */}
                            {/* legacy duplicate category/status controls removed */}
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    Description
                                </label>

                                <textarea
                                    value={description}
                                    onChange={(e) =>
                                        setDescription(e.target.value)
                                    }
                                    placeholder="Describe your project"
                                    rows="4"
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                                    required
                                />
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-300 mb-2">
                                        Start Date
                                    </label>

                                    <input
                                        type="date"
                                        value={startDate}
                                        onChange={(e) =>
                                            setStartDate(e.target.value)
                                        }
                                        className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                        required
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-300 mb-2">
                                        End Date
                                    </label>

                                    <input
                                        type="date"
                                        value={endDate}
                                        min={startDate || undefined}
                                        onChange={(e) =>
                                            setEndDate(e.target.value)
                                        }
                                        className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                        required
                                    />
                                </div>
                            </div>


                            <div className="flex justify-end gap-3 pt-3">
                                <button
                                    type="button"
                                    onClick={() =>
                                        setShowForm(false)
                                    }
                                    className="px-5 py-3 rounded-lg bg-gray-800 hover:bg-gray-700 transition"
                                >
                                    Cancel
                                </button>

                                <button
                                    type="submit"
                                    disabled={creating}
                                    className="px-5 py-3 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-blue-800 disabled:cursor-not-allowed transition"
                                >
                                    {creating
                                        ? editingProject
                                            ? "Updating..."
                                            : "Creating..."
                                        : editingProject
                                        ? "Update Project"
                                        : "Create Project"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {selectedProject && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center px-4 z-50">
                    <div className="w-full max-w-3xl max-h-[90vh] overflow-y-auto bg-gray-900 border border-gray-800 rounded-2xl p-6 sm:p-8">
                        <div className="flex items-center justify-between mb-6">
                            <h2 className="text-2xl font-semibold">
                                {selectedProject.name}
                            </h2>

                            <button
                                onClick={() =>
                                    setSelectedProject(null)
                                }
                                className="text-gray-400 hover:text-white text-xl"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="space-y-5">
                            <div><p className="text-gray-500 text-sm">Access</p><p className="mt-1 text-gray-300">{selectedProject.teamName || "Shared project"}</p></div>
                            <div>
                                <p className="text-gray-500 text-sm">
                                    Project ID
                                </p>

                                <p className="text-white mt-1">
                                    #{selectedProject.id}
                                </p>
                            </div>

                            <div>
                                <p className="text-gray-500 text-sm">
                                    Description
                                </p>

                                <p className="text-gray-300 mt-1">
                                    {selectedProject.description}
                                </p>
                            </div>

                            <div>
                                <p className="text-gray-500 text-sm">
                                    Status
                                </p>

                                <span className="inline-block mt-2 text-xs px-3 py-1 rounded-full bg-blue-500/10 text-blue-400">
                                    {dropdownLabel(statusOptions,selectedProject.status,selectedProject.status)}
                                </span>
                            </div>

                            <div>
                                <p className="text-gray-500 text-sm">
                                    Start Date
                                </p>

                                <p className="text-gray-300 mt-1">
                                    {selectedProject.startDate ||
                                        "Not set"}
                                </p>
                            </div>

                            <div>
                                <p className="text-gray-500 text-sm">
                                    Deadline
                                </p>

                                <p className="text-gray-300 mt-1">
                                    {selectedProject.endDate ||
                                        "Not set"}
                                </p>
                            </div>

                            <div className="pt-4">
                                {canManage && <button
                                    onClick={() => {
                                        setSelectedProject(null);
                                        openEditForm(
                                            selectedProject
                                        );
                                    }}
                                    className="px-5 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg transition"
                                >
                                    Edit Project
                                </button>}
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </main>
    );
}

export default Project;

async function responseError(response, fallback) {
    const text = await response.text();
    if (!text) return fallback;
    try {
        const data = JSON.parse(text);
        return data.message || data.error || data.detail || fallback;
    } catch {
        return text;
    }
}
