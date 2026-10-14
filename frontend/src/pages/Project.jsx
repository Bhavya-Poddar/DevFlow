import { useEffect, useState } from "react";

function Project() {
    const [projects, setProjects] = useState([]);
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [status, setStatus] = useState("ACTIVE");
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
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
                "http://localhost:8080/api/projects",
                {
                    method: "GET",
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            ), fetch("http://localhost:8080/api/users/me", { headers: { Authorization: `Bearer ${token}` } })]);

            if (!response.ok) {
                throw new Error("Failed to fetch projects");
            }

            const data = await response.json();
            setProjects(Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : []);
            if (userResponse.ok) setCurrentUser(await userResponse.json());
        } catch (error) {
            setError(error.message);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        void Promise.resolve().then(fetchProjects);
    }, []);

    const openCreateForm = () => {
        setEditingProject(null);
        setName("");
        setDescription("");
        setStatus("ACTIVE");
        setStartDate("");
        setEndDate("");
        setError("");
        setNotice("");
        setShowForm(true);
    };

    const openEditForm = (project) => {
        setEditingProject(project);
        setName(project.name);
        setDescription(project.description);
        setStatus(project.status);
        setStartDate(project.startDate || "");
        setEndDate(project.endDate || "");
        setError("");
        setShowForm(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (startDate && endDate && endDate < startDate) {
            setError("End date cannot be before start date");
            return;
        }

        setError("");
        setCreating(true);

        const token = localStorage.getItem("token");

        try {
            const url = editingProject
                ? `http://localhost:8080/api/projects/${editingProject.id}`
                : "http://localhost:8080/api/projects";

            const method = editingProject ? "PUT" : "POST";

            const response = await fetch(url, {
                method: method,
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify({
                    name: name,
                    description: description,
                    status: status,
                    startDate: startDate,
                    endDate: endDate
                })
            });

            if (!response.ok) {
                throw new Error(await responseError(response, editingProject ? "Failed to update project." : "Failed to create project."));
            }

            setName("");
            setDescription("");
            setStatus("ACTIVE");
            setStartDate("");
            setEndDate("");
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
        const confirmed = window.confirm(
            "Are you sure you want to delete this project?"
        );

        if (!confirmed) {
            return;
        }

        setError("");
        setNotice("");
        setDeleting(true);

        const token = localStorage.getItem("token");

        try {
            const response = await fetch(
                `http://localhost:8080/api/projects/${id}`,
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
        <div>
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-3xl font-bold">
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
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {projects.map((project) => (
                        <div
                            key={project.id}
                            className="bg-gray-900 border border-gray-800 rounded-xl p-6"
                        >
                            <div className="flex items-start justify-between gap-4">
                                <h2 className="text-xl font-semibold">
                                    {project.name}
                                </h2>

                                <span className="text-xs px-3 py-1 rounded-full bg-blue-500/10 text-blue-400">
                                    {project.status}
                                </span>
                            </div>

                            <p className="text-gray-400 mt-4 text-sm">
                                {project.description}
                            </p>

                            <div className="mt-4 space-y-1">
                                <p className="text-xs text-gray-500">
                                    Start:{" "}
                                    <span className="text-gray-400">
                                        {project.startDate || "Not set"}
                                    </span>
                                </p>

                                <p className="text-xs text-gray-500">
                                    Deadline:{" "}
                                    <span className="text-gray-400">
                                        {project.endDate || "Not set"}
                                    </span>
                                </p>
                            </div>

                            <div className="flex gap-3 mt-6">
                                <button
                                    onClick={() =>
                                        setSelectedProject(project)
                                    }
                                    className="text-sm px-4 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg transition"
                                >
                                    View
                                </button>

                                {canManage && <button
                                    onClick={() =>
                                        openEditForm(project)
                                    }
                                    className="text-sm px-4 py-2 bg-blue-600/10 text-blue-400 hover:bg-blue-600/20 rounded-lg transition"
                                >
                                    Edit
                                </button>}

                                {canManage && <button
                                    onClick={() =>
                                        handleDelete(project.id)
                                    }
                                    disabled={deleting}
                                    className="text-sm px-4 py-2 bg-red-500/10 text-red-400 hover:bg-red-500/20 rounded-lg transition"
                                >
                                    Delete
                                </button>}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {showForm && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center px-4 z-50">
                    <div className="w-full max-w-lg bg-gray-900 border border-gray-800 rounded-2xl p-8">
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

                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    Status
                                </label>

                                <select
                                    value={status}
                                    onChange={(e) =>
                                        setStatus(e.target.value)
                                    }
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                >
                                    <option value="ACTIVE">
                                        ACTIVE
                                    </option>

                                    <option value="IN_PROGRESS">
                                        IN_PROGRESS
                                    </option>

                                    <option value="COMPLETED">
                                        COMPLETED
                                    </option>
                                </select>
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
                    <div className="w-full max-w-lg bg-gray-900 border border-gray-800 rounded-2xl p-8">
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
                                    {selectedProject.status}
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
        </div>
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
