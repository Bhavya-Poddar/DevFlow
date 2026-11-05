import { apiUrl } from "../services/apiConfig";
import { useEffect, useState } from "react";
import RecordTable from "../components/RecordTable";
import { loadDropdownOptions, dropdownLabel } from "../services/dropdownOptions";

function Sprints() {
    const [sprints, setSprints] = useState([]);
    const [projects, setProjects] = useState([]);
    const [issues, setIssues] = useState([]);
    const [boardSprint, setBoardSprint] = useState("ALL");
    const [viewMode, setViewMode] = useState("LIST");
    const [role, setRole] = useState("VIEWER");
    const canManage = ["ADMIN", "PROJECT_MANAGER"].includes(role);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const [showForm, setShowForm] = useState(false);
    const [creating, setCreating] = useState(false);
    const [selectedSprint, setSelectedSprint] = useState(null);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

    const [name, setName] = useState("");
    const [goal, setGoal] = useState("");
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
    const [status, setStatus] = useState("PLANNED");
    const [statusOptions, setStatusOptions] = useState([]);
    const [priorityOptions, setPriorityOptions] = useState([]);
    const [projectId, setProjectId] = useState("");

    const fetchSprints = async () => {
        const token = localStorage.getItem("token");

        try {
            const response = await fetch(apiUrl("/api/sprints"), {
                headers: { Authorization: `Bearer ${token}` }
            });

            if (!response.ok) throw new Error("Failed to fetch sprints");

            setSprints(await response.json());
        } catch (error) {
            setError(error.message);
        } finally {
            setLoading(false);
        }
    };

    const fetchProjects = async () => {
        const token = localStorage.getItem("token");

        try {
            const response = await fetch(apiUrl("/api/projects"), {
                headers: { Authorization: `Bearer ${token}` }
            });

            if (!response.ok) throw new Error("Failed to fetch projects");

            setProjects(await response.json());
        } catch (error) {
            setError(error.message);
        }
    };

    const fetchIssues = async () => {
        try {
            const response = await fetch(apiUrl("/api/issues"), { headers: { Authorization: `Bearer ${localStorage.getItem("token")}` } });
            if (!response.ok) throw new Error("Failed to load sprint tasks");
            setIssues(await response.json());
        } catch (error) { setError(error.message); }
    };

    useEffect(() => {
        loadDropdownOptions("SPRINT_STATUS").then(setStatusOptions).catch(() => setStatusOptions([]));
        loadDropdownOptions("PRIORITY").then(setPriorityOptions).catch(() => setPriorityOptions([]));
        void Promise.resolve().then(() => Promise.all([
            fetchSprints(),
            fetchProjects(),
            fetchIssues(),
            fetch(apiUrl("/api/users/me"), { headers: { Authorization: `Bearer ${localStorage.getItem("token")}` } }).then(r => r.ok ? r.json() : null).then(user => { if (user?.role) setRole(user.role); })
        ]));
    }, []);

    const resetForm = () => {
        setName("");
        setGoal("");
        setStartDate("");
        setEndDate("");
        setStatus("PLANNED");
        setProjectId("");
    };

    const openCreateForm = () => {
        resetForm();
        setError("");
        setShowForm(true);
    };

    const closeCreateForm = () => {
        if (!creating) setShowForm(false);
    };

    const handleCreateSprint = async (e) => {
        e.preventDefault();
        setError("");
        setCreating(true);

        const token = localStorage.getItem("token");

        try {
            const response = await fetch(apiUrl("/api/sprints"), {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify({
                    name,
                    goal,
                    startDate,
                    endDate,
                    status,
                    project: { id: Number(projectId) }
                })
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || "Failed to create sprint");
            }

            setShowForm(false);
            resetForm();
            await fetchSprints();
        } catch (error) {
            setError(error.message);
        } finally {
            setCreating(false);
        }
    };

    const openSprintDetails = (sprint) => {
        setSelectedSprint(sprint);
        setEditing(false);
        setError("");
    };

    const editSprint = (sprint) => {
        if (!sprint) return;
        setSelectedSprint(sprint);
        setName(sprint.name || "");
        setGoal(sprint.goal || "");
        setStartDate(sprint.startDate || "");
        setEndDate(sprint.endDate || "");
        setStatus(sprint.status || "PLANNED");
        setProjectId(sprint.project ? String(sprint.project.id) : "");
        setError("");
        setEditing(true);
    };

    const startEditing = () => editSprint(selectedSprint);

    const cancelEditing = () => {
        if (saving) return;
        setEditing(false);
        setError("");
    };

    const handleUpdateSprint = async (e) => {
        e.preventDefault();
        if (!selectedSprint) return;

        setError("");
        setSaving(true);

        const token = localStorage.getItem("token");

        try {
            const response = await fetch(apiUrl(`/api/sprints/${selectedSprint.id}`), {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify({
                    name,
                    goal,
                    startDate,
                    endDate,
                    status,
                    project: projectId ? { id: Number(projectId) } : null
                })
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || "Failed to update sprint");
            }

            const updatedSprint = await response.json();

            setSprints((current) =>
                current.map((sprint) =>
                    sprint.id === updatedSprint.id ? updatedSprint : sprint
                )
            );

            setSelectedSprint(updatedSprint);
            setEditing(false);
        } catch (error) {
            setError(error.message);
        } finally {
            setSaving(false);
        }
    };

    const handleDeleteSprint = async () => {
        if (!selectedSprint) return;

        setDeleting(true);
        setError("");

        const token = localStorage.getItem("token");

        try {
            const response = await fetch(apiUrl(`/api/sprints/${selectedSprint.id}`), {
                method: "DELETE",
                headers: { Authorization: `Bearer ${token}` }
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || "Failed to delete sprint");
            }

            setSprints((current) =>
                current.filter((sprint) => sprint.id !== selectedSprint.id)
            );

            setShowDeleteConfirm(false);
            setSelectedSprint(null);
        } catch (error) {
            setError(error.message);
        } finally {
            setDeleting(false);
        }
    };

    const closeDetails = () => {
        if (saving || deleting) return;
        setSelectedSprint(null);
        setEditing(false);
        setShowDeleteConfirm(false);
    };

    const getStatusClass = (sprintStatus) => {
        if (sprintStatus === "ACTIVE") return "bg-green-500/20 text-green-400";
        if (sprintStatus === "COMPLETED") return "bg-blue-500/20 text-blue-400";
        return "bg-yellow-500/20 text-yellow-400";
    };
    const columns = [["OPEN","TODO"],["IN_PROGRESS","IN PROGRESS"],["CODE_REVIEW","CODE REVIEW"],["READY_FOR_TESTING","READY FOR TESTING"],["TESTING","TESTING"],["RESOLVED","RESOLVED"],["CLOSED","CLOSED"]];
    const boardIssues = issues.filter(issue => boardSprint === "ALL" ? true : String(issue.sprint?.id ?? issue.sprintId ?? "") === boardSprint);

    return (
        <main className="dfx-screen dfx-screen--sprints">
            <div className="dfx-screen-header">
                <div>
                    <p className="mb-2 text-xs font-semibold uppercase tracking-[.16em] text-blue-300">DELIVERY / PLANNING</p><h1 className="text-3xl font-bold text-white">Sprint Board</h1>
                    <p className="text-gray-400 mt-1">Follow task work through engineering and testing.</p>
                </div>

                {canManage && <button
                    onClick={openCreateForm}
                    className="px-5 py-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium transition"
                >
                    + Create Sprint
                </button>}
            </div>

            <div className="dfx-sprint-overview" aria-label="Sprint overview"><article><span>Planned</span><strong>{sprints.filter(sprint => sprint.status === "PLANNED").length}</strong><small>Ready to begin</small></article><article><span>Active</span><strong>{sprints.filter(sprint => sprint.status === "ACTIVE").length}</strong><small>In progress</small></article><article><span>Tasks on board</span><strong>{boardIssues.length}</strong><small>{boardSprint === "ALL" ? "Across all sprints" : "In selected sprint"}</small></article></div>

            {error && (
                <div className="mb-6 px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400">
                    {error}
                </div>
            )}

            <section className="mb-5 flex flex-wrap items-center justify-between gap-3"><div className="flex gap-1 rounded-lg border border-gray-800 bg-gray-900 p-1" role="group" aria-label="Sprint view"><button type="button" aria-pressed={viewMode === "LIST"} onClick={() => setViewMode("LIST")} className={`rounded-md px-3 py-2 text-sm ${viewMode === "LIST" ? "bg-blue-600 text-white" : "text-gray-400 hover:text-white"}`}>Sprint list</button><button type="button" aria-pressed={viewMode === "BOARD"} onClick={() => setViewMode("BOARD")} className={`rounded-md px-3 py-2 text-sm ${viewMode === "BOARD" ? "bg-blue-600 text-white" : "text-gray-400 hover:text-white"}`}>Task workflow</button></div>{viewMode === "BOARD" && <label className="flex items-center gap-2 text-sm text-gray-400">Sprint<select aria-label="Select sprint" value={boardSprint} onChange={e => setBoardSprint(e.target.value)} className="rounded-lg border border-gray-700 bg-gray-900 px-3 py-2 text-sm text-white"><option value="ALL">All sprints</option>{sprints.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>}</section>

            {viewMode === "BOARD" && <section className="mb-8 space-y-4"><div className="grid gap-3 overflow-x-auto pb-3 md:grid-cols-2 xl:grid-cols-4 2xl:grid-cols-7">{columns.map(([status,title])=><div key={status} className="min-w-[220px] rounded-xl border border-gray-800 bg-gray-900 p-3"><div className="mb-3 flex items-center justify-between"><h3 className="text-xs font-semibold uppercase tracking-wide text-gray-300">{dropdownLabel(statusOptions,status,title)}</h3><span className="rounded-full bg-gray-800 px-2 py-0.5 text-xs text-gray-400">{boardIssues.filter(i=>i.status===status).length}</span></div><div className="space-y-2">{boardIssues.filter(i=>i.status===status).map(issue=><article key={issue.id} className="rounded-lg border border-gray-800 bg-gray-950 p-3"><p className="text-sm font-medium text-white">{issue.title||`Task #${issue.id}`}</p><p className="mt-1 text-xs text-gray-500">{issue.project?.name||"No project"} · {dropdownLabel(priorityOptions,issue.priority,issue.priority||"—")}</p><p className="mt-2 text-xs text-gray-400">Developer: {issue.assignedUser?.name||"Unassigned"}</p><p className="mt-1 text-xs text-gray-400">Tester: {issue.tester?.name||"Unassigned"}</p><p className="mt-1 text-xs text-blue-300">{dropdownLabel(statusOptions,issue.status,String(issue.status||"").replaceAll("_"," "))}</p></article>)}{boardIssues.every(i=>i.status!==status)&&<p className="py-4 text-center text-xs text-gray-600">No tasks</p>}</div></div>)}</div></section>}

            {loading ? (
                <div className="text-gray-400">Loading sprints...</div>
            ) : sprints.length === 0 ? (
                <div className="bg-gray-900 border border-gray-800 rounded-xl p-10 text-center">
                    <h2 className="text-xl font-semibold text-white mb-2">No sprints yet</h2>
                    <p className="text-gray-400">Create your first sprint to get started.</p>
                </div>
            ) : (
                <div className="df-panel overflow-hidden">
                    <RecordTable rows={sprints} searchLabel="Search sprints" searchPlaceholder="Search sprints by name, goal, or project…" emptyLabel="No sprints match your search." getSearchText={sprint => `${sprint.name || ""} ${sprint.goal || ""} ${sprint.project?.name || ""} ${sprint.status || ""}`} columns={[
                        { key: "name", label: "Sprint", sortValue: sprint => sprint.name, render: sprint => <button type="button" onClick={() => openSprintDetails(sprint)} className="min-w-40 text-left font-semibold text-white hover:text-blue-300">{sprint.name || "Untitled sprint"}<span className="mt-1 block max-w-sm truncate text-xs font-normal text-gray-500">{sprint.goal || "No sprint goal"}</span></button> },
                        { key: "project", label: "Project", sortValue: sprint => sprint.project?.name || "", render: sprint => sprint.project?.name || "—" },
                        { key: "start", label: "Start date", sortValue: sprint => sprint.startDate || "", render: sprint => sprint.startDate || "—" },
                        { key: "end", label: "End date", sortValue: sprint => sprint.endDate || "", render: sprint => sprint.endDate || "—" },
                        { key: "status", label: "Status", sortValue: sprint => sprint.status, render: sprint => <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${getStatusClass(sprint.status)}`}>{dropdownLabel(statusOptions, sprint.status, sprint.status?.replaceAll("_", " ") || "Unknown")}</span> }
                    ]} actionsForRow={sprint => [
                        { label: "View details", onSelect: () => openSprintDetails(sprint) },
                        ...(canManage ? [
                            { label: "Edit sprint", onSelect: () => editSprint(sprint) },
                            { label: "Delete sprint", danger: true, onSelect: () => { setSelectedSprint(sprint); setShowDeleteConfirm(true); } }
                        ] : [])
                    ]} />
                </div>
            )}

            {showForm && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-40 px-4">
                    <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-lg shadow-2xl">
                        <div className="flex items-center justify-between mb-6">
                            <h2 className="text-xl font-semibold text-white">Create Sprint</h2>
                            <button onClick={closeCreateForm} className="text-gray-400 hover:text-white text-xl">✕</button>
                        </div>

                        <form onSubmit={handleCreateSprint} className="space-y-4">
                            <div>
                                <label className="block text-sm text-gray-400 mb-2">Sprint Name</label>
                                <input
                                    type="text"
                                    value={name}
                                    onChange={(e) => setName(e.target.value)}
                                    required
                                    placeholder="Sprint 1"
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:border-blue-500"
                                />
                            </div>

                            <div>
                                <label className="block text-sm text-gray-400 mb-2">Goal</label>
                                <textarea
                                    value={goal}
                                    onChange={(e) => setGoal(e.target.value)}
                                    required
                                    rows="3"
                                    placeholder="Complete authentication module"
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:border-blue-500 resize-none"
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm text-gray-400 mb-2">Start Date</label>
                                    <input
                                        type="date"
                                        value={startDate}
                                        onChange={(e) => setStartDate(e.target.value)}
                                        required
                                        className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm text-gray-400 mb-2">End Date</label>
                                    <input
                                        type="date"
                                        value={endDate}
                                        onChange={(e) => setEndDate(e.target.value)}
                                        required
                                        className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm text-gray-400 mb-2">Status</label>
                                    <select
                                        value={status}
                                        onChange={(e) => setStatus(e.target.value)}
                                        className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                    >
                                        {(statusOptions.length ? statusOptions : ["PLANNED", "ACTIVE", "COMPLETED"].map(code => ({code,displayLabel:code.replaceAll("_"," ")}))).map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm text-gray-400 mb-2">Project</label>
                                    <select
                                        value={projectId}
                                        onChange={(e) => setProjectId(e.target.value)}
                                        required
                                        className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                    >
                                        <option value="">Select project</option>
                                        {projects.map((project) => (
                                            <option key={project.id} value={project.id}>{project.name}</option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            <div className="flex justify-end gap-3 pt-2">
                                <button
                                    type="button"
                                    onClick={closeCreateForm}
                                    disabled={creating}
                                    className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 transition"
                                >
                                    Cancel
                                </button>

                                <button
                                    type="submit"
                                    disabled={creating}
                                    className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium transition disabled:opacity-50"
                                >
                                    {creating ? "Creating..." : "Create Sprint"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {selectedSprint && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-40 px-4">
                    <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-lg shadow-2xl">
                        {!editing ? (
                            <>
                                <div className="flex items-start justify-between mb-6">
                                    <div>
                                        <h2 className="text-2xl font-semibold text-white">{selectedSprint.name}</h2>
                                        <p className="text-gray-500 mt-1">Sprint #{selectedSprint.id}</p>
                                    </div>

                                    <button onClick={closeDetails} className="text-gray-400 hover:text-white text-xl">✕</button>
                                </div>

                                <div className="space-y-5">
                                    <div>
                                        <p className="text-sm text-gray-500 mb-1">Goal</p>
                                        <p className="text-gray-300">{selectedSprint.goal}</p>
                                    </div>

                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <p className="text-sm text-gray-500 mb-1">Status</p>
                                            <span className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${getStatusClass(selectedSprint.status)}`}>
                                                {dropdownLabel(statusOptions,selectedSprint.status,selectedSprint.status)}
                                            </span>
                                        </div>

                                        <div>
                                            <p className="text-sm text-gray-500 mb-1">Project</p>
                                            <p className="text-white">{selectedSprint.project?.name || "No project"}</p>
                                        </div>
                                    </div>

                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <p className="text-sm text-gray-500 mb-1">Start Date</p>
                                            <p className="text-white">{selectedSprint.startDate}</p>
                                        </div>

                                        <div>
                                            <p className="text-sm text-gray-500 mb-1">End Date</p>
                                            <p className="text-white">{selectedSprint.endDate}</p>
                                        </div>
                                    </div>
                                </div>

                                <div className="flex justify-between gap-3 mt-8">
                                    {canManage && <button
                                        onClick={() => setShowDeleteConfirm(true)}
                                        className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white font-medium transition"
                                    >
                                        🗑️ Delete
                                    </button>}

                                    <div className="flex gap-3">
                                        <button
                                            onClick={closeDetails}
                                            className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 transition"
                                        >
                                            Close
                                        </button>

                                        {canManage && <button
                                            onClick={startEditing}
                                            className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium transition"
                                        >
                                            Edit Sprint
                                        </button>}
                                    </div>
                                </div>
                            </>
                        ) : (
                            <>
                                <div className="flex items-center justify-between mb-6">
                                    <h2 className="text-xl font-semibold text-white">Edit Sprint</h2>
                                    <button onClick={cancelEditing} className="text-gray-400 hover:text-white text-xl">✕</button>
                                </div>

                                <form onSubmit={handleUpdateSprint} className="space-y-4">
                                    <div>
                                        <label className="block text-sm text-gray-400 mb-2">Sprint Name</label>
                                        <input
                                            type="text"
                                            value={name}
                                            onChange={(e) => setName(e.target.value)}
                                            required
                                            className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:border-blue-500"
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm text-gray-400 mb-2">Goal</label>
                                        <textarea
                                            value={goal}
                                            onChange={(e) => setGoal(e.target.value)}
                                            required
                                            rows="3"
                                            className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:border-blue-500 resize-none"
                                        />
                                    </div>

                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <label className="block text-sm text-gray-400 mb-2">Start Date</label>
                                            <input
                                                type="date"
                                                value={startDate}
                                                onChange={(e) => setStartDate(e.target.value)}
                                                required
                                                className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                            />
                                        </div>

                                        <div>
                                            <label className="block text-sm text-gray-400 mb-2">End Date</label>
                                            <input
                                                type="date"
                                                value={endDate}
                                                onChange={(e) => setEndDate(e.target.value)}
                                                required
                                                className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                            />
                                        </div>
                                    </div>

                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <label className="block text-sm text-gray-400 mb-2">Status</label>
                                            <select
                                                value={status}
                                                onChange={(e) => setStatus(e.target.value)}
                                                className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                            >
                                                {(statusOptions.length ? statusOptions : ["PLANNED", "ACTIVE", "COMPLETED"].map(code => ({code,displayLabel:code.replaceAll("_"," ")}))).map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}
                                            </select>
                                        </div>

                                        <div>
                                            <label className="block text-sm text-gray-400 mb-2">Project</label>
                                            <select
                                                value={projectId}
                                                onChange={(e) => setProjectId(e.target.value)}
                                                required
                                                className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none"
                                            >
                                                <option value="">Select project</option>
                                                {projects.map((project) => (
                                                    <option key={project.id} value={project.id}>{project.name}</option>
                                                ))}
                                            </select>
                                        </div>
                                    </div>



            {error && (
                                        <div className="px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400">
                                            {error}
                                        </div>
                                    )}

                                    <div className="flex justify-end gap-3 pt-2">
                                        <button
                                            type="button"
                                            onClick={cancelEditing}
                                            disabled={saving}
                                            className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 transition"
                                        >
                                            Cancel
                                        </button>

                                        <button
                                            type="submit"
                                            disabled={saving}
                                            className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium transition disabled:opacity-50"
                                        >
                                            {saving ? "Saving..." : "Save Changes"}
                                        </button>
                                    </div>
                                </form>
                            </>
                        )}
                    </div>
                </div>
            )}

            {showDeleteConfirm && selectedSprint && (
                <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 px-4">
                    <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-md shadow-2xl">
                        <h2 className="text-xl font-semibold text-white mb-2">Delete Sprint?</h2>

                        <p className="text-gray-400 mb-6">
                            Are you sure you want to delete{" "}
                            <span className="text-white font-medium">{selectedSprint.name}</span>?
                            This action cannot be undone.
                        </p>

                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setShowDeleteConfirm(false)}
                                disabled={deleting}
                                className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 transition disabled:opacity-50"
                            >
                                Cancel
                            </button>

                            <button
                                onClick={handleDeleteSprint}
                                disabled={deleting}
                                className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white font-medium transition disabled:opacity-50"
                            >
                                {deleting ? "Deleting..." : "Delete Sprint"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </main>
    );
}

export default Sprints;
