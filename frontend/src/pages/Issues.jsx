import { useCallback, useEffect, useMemo, useState } from "react";
import { responseErrorMessage } from "../utils/responseError";
import { apiUrl, listIssues, createIssue, updateIssue, deleteIssue, getIssueActivity } from "../services/issueApi";
import Avatar from "../components/Avatar";

const CANONICAL_STATUSES = ["OPEN", "IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING", "TESTING", "RESOLVED"];

function Issues() {
    const token = localStorage.getItem("token");

    const [issues, setIssues] = useState([]);
    const [projects, setProjects] = useState([]);
    const [users, setUsers] = useState([]);
    const [testers, setTesters] = useState([]);
    const [testersLoading, setTestersLoading] = useState(false);
    const [testerLoadError, setTesterLoadError] = useState("");
    const [sprints, setSprints] = useState([]);
    const [currentUser, setCurrentUser] = useState(null);

    const [selectedIssue, setSelectedIssue] = useState(null);
    const [comments, setComments] = useState([]);
    const [issueActivity, setIssueActivity] = useState([]);
    const [activityLoading, setActivityLoading] = useState(false);
    const [activityError, setActivityError] = useState("");
    const [commentText, setCommentText] = useState("");

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);
    const [isEditMode, setIsEditMode] = useState(false);

    const [formData, setFormData] = useState({
        title: "",
        description: "",
        acceptanceCriteria: "",
        priority: "MEDIUM",
        status: "OPEN",
        projectId: "",
        assignedUserId: "",
        sprintId: ""
    });

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [updatingStatusId, setUpdatingStatusId] = useState(null);
    const [savingComment, setSavingComment] = useState(false);
    const [deletingCommentId, setDeletingCommentId] = useState(null);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    const authHeaders = useMemo(() => ({
        Authorization: `Bearer ${token}`
    }), [token]);

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError("");

            const [issueList, projectsResponse, usersResponse, sprintsResponse, userResponse] = await Promise.all([
                listIssues(token),
                fetch(apiUrl("/api/projects"), { headers: authHeaders }),
                fetch(apiUrl("/api/users/assignable"), { headers: authHeaders }),
                fetch(apiUrl("/api/sprints"), { headers: authHeaders }),
                fetch(apiUrl("/api/users/me"), { headers: authHeaders })
            ]);

            if (!Array.isArray(issueList)) {
                throw new Error("The issues endpoint returned an unexpected response format.");
            }
            if (!projectsResponse.ok) throw new Error(await responseErrorMessage(projectsResponse, "Failed to fetch projects."));
            if (!usersResponse.ok) throw new Error(await responseErrorMessage(usersResponse, "Failed to fetch users."));
            if (!sprintsResponse.ok) throw new Error(await responseErrorMessage(sprintsResponse, "Failed to fetch sprints."));
            if (!userResponse.ok) throw new Error(await responseErrorMessage(userResponse, "Failed to fetch current user."));

            setIssues(issueList);
            setProjects(await projectsResponse.json());
            setUsers(await usersResponse.json());
            setSprints(await sprintsResponse.json());
            const me = await userResponse.json();
            setCurrentUser(me);
            setLoading(false);
            if (["ADMIN", "PROJECT_MANAGER"].includes(me?.role)) {
                setTestersLoading(true);
                setTesterLoadError("");
                try {
                    const testerResponse = await fetch(apiUrl("/api/users/testers"), { headers: authHeaders });
                    if (!testerResponse.ok) {
                        throw new Error(await responseErrorMessage(testerResponse, "Failed to fetch testers."));
                    }
                    const testerData = await testerResponse.json();
                    const testerList = Array.isArray(testerData)
                        ? testerData
                        : Array.isArray(testerData?.content)
                            ? testerData.content
                            : [];
                    setTesters(testerList.filter(user => user?.role === "TESTER"));
                } catch (testerError) {
                    setTesterLoadError(testerError.message);
                    throw testerError;
                } finally {
                    setTestersLoading(false);
                }
            } else {
                setTesters([]);
                setTesterLoadError("");
            }
        } catch (error) {
            setError(error.message);
        } finally {
            setLoading(false);
        }
    }, [authHeaders, token]);

    useEffect(() => {
        void Promise.resolve().then(fetchData);
    }, [fetchData]);

    const fetchComments = async (issueId) => {
        try {
            const response = await fetch(apiUrl(`/api/comments/issue/${encodeURIComponent(issueId)}`), {
                headers: authHeaders
            });

            if (!response.ok) throw new Error(await responseErrorMessage(response, "Failed to fetch comments."));

            setComments(await response.json());
        } catch (error) {
            setError(error.message);
        }
    };

    const fetchIssueActivity = async (issueId) => {
        setActivityLoading(true);
        setActivityError("");
        try {
            const entries = await getIssueActivity(token, issueId);
            if (!Array.isArray(entries)) throw new Error("Issue activity returned an unexpected response format.");
            setIssueActivity(entries);
        } catch (error) {
            setIssueActivity([]);
            setActivityError(error.message);
        } finally {
            setActivityLoading(false);
        }
    };

    const handleInputChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    const resetForm = () => {
        setFormData({
            title: "",
            description: "",
            acceptanceCriteria: "",
            priority: "MEDIUM",
            status: "OPEN",
            projectId: "",
            assignedUserId: "",
            sprintId: "",
            testerId: ""
        });
    };

    const openCreateModal = () => {
        resetForm();
        setIsEditMode(false);
        setIsModalOpen(true);
    };

    const openEditModal = (issue) => {
        setFormData({
            title: issue.title || "",
            description: issue.description || "",
            acceptanceCriteria: issue.acceptanceCriteria || "",
            priority: issue.priority || "MEDIUM",
            status: issue.status || "OPEN",
            projectId: issue.project?.id ?? issue.projectId ?? "",
            assignedUserId: issue.assignedUser?.id ?? issue.assignedUserId ?? "",
            sprintId: issue.sprint?.id ?? issue.sprintId ?? "",
            testerId: issue.tester?.id ?? issue.testerId ?? ""
        });

        setSelectedIssue(issue);
        setIsEditMode(true);
        setIsModalOpen(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSaving(true);
        setError("");
        setNotice("");

        try {
            const issueData = isEditMode ? {} : {
                title: formData.title,
                description: formData.description,
                acceptanceCriteria: formData.acceptanceCriteria,
                priority: formData.priority
            };
            if (isEditMode) {
                if (formData.title !== (selectedIssue?.title ?? "")) issueData.title = formData.title;
                if (formData.description !== (selectedIssue?.description ?? "")) issueData.description = formData.description;
                if (formData.acceptanceCriteria !== (selectedIssue?.acceptanceCriteria ?? "")) issueData.acceptanceCriteria = formData.acceptanceCriteria;
                if (formData.priority !== (selectedIssue?.priority ?? "MEDIUM")) issueData.priority = formData.priority;
                if (formData.status !== selectedIssue?.status) issueData.status = formData.status;
                const previousProjectId = selectedIssue?.project?.id ?? selectedIssue?.projectId ?? "";
                const previousDeveloperId = selectedIssue?.assignedUser?.id ?? selectedIssue?.assignedUserId ?? "";
                const previousSprintId = selectedIssue?.sprint?.id ?? selectedIssue?.sprintId ?? "";
                const previousTesterId = selectedIssue?.tester?.id ?? selectedIssue?.testerId ?? "";
                const sameId = (next, previous) => String(next ?? "") === String(previous ?? "");

                if (!sameId(formData.projectId, previousProjectId)) {
                    issueData.project = formData.projectId ? { id: Number(formData.projectId) } : null;
                }
                if (["ADMIN", "PROJECT_MANAGER"].includes(currentUser?.role)) {
                    if (!sameId(formData.assignedUserId, previousDeveloperId)) {
                        issueData.assignedUser = formData.assignedUserId
                            ? { id: Number(formData.assignedUserId) }
                            : null;
                    }
                    if (!sameId(formData.sprintId, previousSprintId)) {
                        issueData.sprint = formData.sprintId ? { id: Number(formData.sprintId) } : null;
                    }
                    if (!sameId(formData.testerId, previousTesterId)) {
                        issueData.tester = formData.testerId ? { id: Number(formData.testerId) } : null;
                    }
                }
            } else {
                issueData.project = formData.projectId ? { id: Number(formData.projectId) } : null;
            }

            const savedIssue = isEditMode
                ? await updateIssue(token, selectedIssue.id, issueData)
                : await createIssue(token, issueData);

            if (isEditMode) {
                setIssues((prev) =>
                    prev.map((issue) => issue.id === savedIssue.id ? savedIssue : issue)
                );
            } else {
                setIssues((prev) => [...prev, savedIssue]);
            }

            setIsModalOpen(false);
            setSelectedIssue(null);
            resetForm();
            setNotice(isEditMode ? "Issue updated successfully." : "Issue created successfully.");
        } catch (error) {
            setError(error.message);
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm("Are you sure you want to delete this issue?")) return;

        try {
            setDeletingId(id);
            setError("");
            setNotice("");
            await deleteIssue(token, id);

            setIssues((prev) => prev.filter((issue) => issue.id !== id));
            setNotice("Issue deleted successfully.");
        } catch (error) {
            setError(error.message);
        } finally {
            setDeletingId(null);
        }
    };

    const handleViewIssue = (issue) => {
        setSelectedIssue(issue);
        setIsViewModalOpen(true);
        setIsEditMode(false);
        setComments([]);
        setIssueActivity([]);
        setActivityError("");
        setCommentText("");

        fetchComments(issue.id);
        fetchIssueActivity(issue.id);
    };

    const canManage = ["ADMIN", "PROJECT_MANAGER"].includes(currentUser?.role);
    const updateStatus = async (issue, status) => {
        setUpdatingStatusId(issue.id);
        setError("");
        setNotice("");
        try {
            const updated = await updateIssue(token, issue.id, { status }); setIssues(old => old.map(item => item.id === updated.id ? updated : item));
            setSelectedIssue(updated);
            fetchIssueActivity(issue.id);
            setNotice("Issue status updated successfully.");
        } catch (e) { setError(e.message); }
        finally { setUpdatingStatusId(null); }
    };

    const handleAddComment = async () => {
        if (!commentText.trim() || !selectedIssue || !currentUser) return;

        try {
            setSavingComment(true);
            const response = await fetch(apiUrl("/api/comments"), {
                method: "POST",
                headers: {
                    ...authHeaders,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    content: commentText,
                    issue: { id: selectedIssue.id },
                })
            });

            if (!response.ok) throw new Error(await responseErrorMessage(response, "Failed to add comment."));

            setCommentText("");
            await fetchComments(selectedIssue.id);
            await fetchIssueActivity(selectedIssue.id);
        } catch (error) {
            setError(error.message);
        } finally {
            setSavingComment(false);
        }
    };

    const handleCommentKeyDown = (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleAddComment();
        }
    };

    const handleDeleteComment = async (commentId) => {
        try {
            setDeletingCommentId(commentId);
            const response = await fetch(apiUrl(`/api/comments/${encodeURIComponent(commentId)}`), {
                method: "DELETE",
                headers: authHeaders
            });

            if (!response.ok) throw new Error(await responseErrorMessage(response, "Failed to delete comment."));

            setComments((prev) => prev.filter((comment) => comment.id !== commentId));

            if (selectedIssue) {
                await fetchIssueActivity(selectedIssue.id);
            }
        } catch (error) {
            setError(error.message);
        } finally {
            setDeletingCommentId(null);
        }
    };

    const getStatusClass = (status) => {
        if (status === "OPEN") return "bg-gray-700 text-gray-200";
        if (status === "IN_PROGRESS") return "bg-blue-500/20 text-blue-400";
        if (status === "CODE_REVIEW") return "bg-purple-500/20 text-purple-400";
        if (status === "READY_FOR_TESTING") return "bg-cyan-500/20 text-cyan-300";
        if (status === "TESTING") return "bg-yellow-500/20 text-yellow-400";
        if (status === "RESOLVED") return "bg-green-500/20 text-green-400";
        if (status === "CLOSED") return "bg-gray-500/20 text-gray-400";
        return "bg-gray-700 text-gray-200";
    };

    const getPriorityClass = (priority) => {
        if (priority === "CRITICAL") return "bg-red-500/20 text-red-400";
        if (priority === "HIGH") return "bg-orange-500/20 text-orange-400";
        if (priority === "MEDIUM") return "bg-yellow-500/20 text-yellow-400";
        return "bg-green-500/20 text-green-400";
    };

    const formatAction = (action) => {
        return action
            ? action.replaceAll("_", " ")
            : "Activity";
    };

    const formatDate = (date) => {
        if (!date) return "";
        return new Date(date).toLocaleString();
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-950 flex items-center justify-center">
                <p className="text-gray-400">Loading issues...</p>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-950 text-white p-6">
            <div className="max-w-7xl mx-auto">

                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-3xl font-bold">Issues</h1>
                        <p className="text-gray-400 mt-1">
                            Manage and track project issues
                        </p>
                    </div>

                    {["ADMIN", "PROJECT_MANAGER", "DEVELOPER"].includes(currentUser?.role) && <button
                        onClick={openCreateModal}
                        className="px-5 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg font-semibold transition"
                    >
                        + Create Issue
                    </button>}
                </div>

                {error && (
                    <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 flex justify-between">
                        <span>{error}</span>
                        <button
                            onClick={() => setError("")}
                            className="text-red-300 hover:text-white"
                        >
                            ✕
                        </button>
                    </div>
                )}
                {notice && <div role="status" className="mb-6 rounded-lg border border-emerald-500/20 bg-emerald-500/10 p-4 text-emerald-300">{notice}</div>}

                {issues.length === 0 && !error ? (
                    <div className="bg-gray-900 border border-gray-800 rounded-xl p-12 text-center">
                        <h2 className="text-xl font-semibold">No issues found</h2>
                        <p className="text-gray-400 mt-2">
                            Create your first issue to get started.
                        </p>
                    </div>
                ) : (
                    <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead className="bg-gray-800">
                                    <tr>
                                        <th className="text-left px-6 py-4 text-sm text-gray-400">Title</th>
                                        <th className="text-left px-6 py-4 text-sm text-gray-400">Project</th>
                                        <th className="text-left px-6 py-4 text-sm text-gray-400">Assignee</th>
                                        <th className="text-left px-6 py-4 text-sm text-gray-400">Sprint</th>
                                        <th className="text-left px-6 py-4 text-sm text-gray-400">Priority</th>
                                        <th className="text-left px-6 py-4 text-sm text-gray-400">Status</th>
                                        <th className="text-right px-6 py-4 text-sm text-gray-400">Actions</th>
                                    </tr>
                                </thead>

                                <tbody className="divide-y divide-gray-800">
                                    {issues.map((issue) => (
                                        <tr key={issue.id} className="hover:bg-gray-800/50 transition">
                                            <td className="px-6 py-4">
                                                <button
                                                    onClick={() => handleViewIssue(issue)}
                                                    className="text-white font-medium hover:text-blue-400 transition text-left"
                                                >
                                                    {issue.title}
                                                </button>
                                                <p className="text-xs text-gray-500 mt-1">
                                                    #{issue.id}
                                                </p>
                                            </td>

                                            <td className="px-6 py-4 text-gray-300">
                                                {issue.project?.name || "No Project"}
                                            </td>

                                            <td className="px-6 py-4 text-gray-300">
                                                {issue.assignedUser?.name || "Unassigned"}
                                            </td>

                                            <td className="px-6 py-4 text-gray-300">
                                                {issue.sprint?.name || "No Sprint"}
                                            </td>

                                            <td className="px-6 py-4">
                                                <span className={`px-3 py-1 rounded-full text-xs font-medium ${getPriorityClass(issue.priority)}`}>
                                                    {issue.priority}
                                                </span>
                                            </td>

                                            <td className="px-6 py-4">
                                                <span className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusClass(issue.status)}`}>
                                                    {issue.status?.replaceAll("_", " ")}
                                                </span>
                                            </td>

                                            <td className="px-6 py-4">
                                                <div className="flex justify-end gap-2">
                                                    <button
                                                        onClick={() => handleViewIssue(issue)}
                                                        className="px-3 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg text-sm"
                                                    >
                                                        View
                                                    </button>

                                                    {(canManage || (currentUser?.role === "DEVELOPER" && String(issue.assignedUser?.id) === String(currentUser.id))) && <button
                                                        onClick={() => openEditModal(issue)}
                                                        className="px-3 py-2 bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 rounded-lg text-sm"
                                                    >
                                                        Edit
                                                    </button>}

                                                    {canManage && <button
                                                        onClick={() => handleDelete(issue.id)}
                                                        disabled={deletingId === issue.id}
                                                        className="px-3 py-2 bg-red-600/20 hover:bg-red-600/30 text-red-400 rounded-lg text-sm"
                                                    >
                                                        {deletingId === issue.id ? "Deleting…" : "Delete"}
                                                    </button>}
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>

            {isModalOpen && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
                    <div className="bg-gray-900 border border-gray-800 rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">

                        <div className="flex justify-between items-center p-6 border-b border-gray-800">
                            <div>
                                <h2 className="text-2xl font-semibold">
                                    {isEditMode ? "Edit Issue" : "Create Issue"}
                                </h2>
                                <p className="text-gray-400 text-sm mt-1">
                                    {isEditMode ? "Update issue details" : "Create a new issue"}
                                </p>
                            </div>

                            <button
                                onClick={() => setIsModalOpen(false)}
                                className="text-gray-400 hover:text-white text-xl"
                            >
                                ✕
                            </button>
                        </div>

                        <form onSubmit={handleSubmit} className="p-6 space-y-5">

                            <div>
                                <label className="block text-sm text-gray-300 mb-2">
                                    Title
                                </label>
                                <input
                                    type="text"
                                    name="title"
                                    value={formData.title}
                                    onChange={handleInputChange}
                                    placeholder="Enter issue title"
                                    required
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                />
                            </div>

                            <div>
                                <label className="block text-sm text-gray-300 mb-2">
                                    Description
                                </label>
                                <textarea
                                    name="description"
                                    value={formData.description}
                                    onChange={handleInputChange}
                                    placeholder="Describe the issue"
                                    rows="4"
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                />
                            </div>
                            <div>
                                <label className="block text-sm text-gray-300 mb-2">Acceptance criteria</label>
                                <textarea
                                    name="acceptanceCriteria"
                                    value={formData.acceptanceCriteria}
                                    onChange={handleInputChange}
                                    placeholder="Describe the conditions that must pass testing"
                                    rows="3"
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                />
                            </div>

                            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                                <div><label className="mb-2 block text-sm text-gray-300">Priority</label><select name="priority" value={formData.priority} onChange={handleInputChange} className="w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white"><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select></div>
                                {isEditMode && canManage && <div><label className="mb-2 block text-sm text-gray-300">Status</label><select name="status" value={formData.status} onChange={handleInputChange} className="w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white">
                                    {!CANONICAL_STATUSES.includes(formData.status) && <option value={formData.status}>{formData.status} (existing status)</option>}
                                    {CANONICAL_STATUSES.map(status => <option key={status} value={status} disabled={["TESTING", "RESOLVED"].includes(status) && selectedIssue?.status !== status}>{status}</option>)}
                                </select>{formData.status === "TESTING" && selectedIssue?.status !== "TESTING" && <p className="mt-1 text-xs text-gray-500">An assigned tester starts testing from the testing workspace.</p>}</div>}
                            </div>
                            <div><label className="mb-2 block text-sm text-gray-300">Project</label><select name="projectId" value={formData.projectId} onChange={handleInputChange} required={!isEditMode} disabled={isEditMode && !canManage} className="w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white disabled:opacity-60"><option value="">Select Project</option>{projects.map(project=><option key={project.id} value={project.id}>{project.name}</option>)}</select></div>
                            {isEditMode && canManage && <>
                              <div><label className="mb-2 block text-sm text-gray-300">Assign Developer</label><select name="assignedUserId" value={formData.assignedUserId} onChange={handleInputChange} className="w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white"><option value="">Unassigned</option>{users.filter(u=>u.role==="DEVELOPER").map(u=><option key={u.id} value={u.id}>{u.name}</option>)}</select></div>
                              <div><label className="mb-2 block text-sm text-gray-300">Assign Tester</label><select name="testerId" value={formData.testerId ?? ""} onChange={handleInputChange} disabled={testersLoading || saving} className="w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white disabled:opacity-60"><option value="">{testersLoading ? "Loading testers…" : "Unassigned"}</option>{testers.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select>{testerLoadError ? <p role="alert" className="mt-1 text-xs text-red-300">Could not load testers: {testerLoadError}</p> : !testersLoading && testers.length === 0 && <p className="mt-1 text-xs text-gray-400">No TESTER users are available.</p>}</div>
                              <div><label className="mb-2 block text-sm text-gray-300">Sprint</label><select name="sprintId" value={formData.sprintId} onChange={handleInputChange} className="w-full rounded-lg border border-gray-700 bg-gray-800 px-4 py-3 text-white"><option value="">No Sprint</option>{sprints.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
                            </>}

                            <div className="flex justify-end gap-3 pt-4">
                                <button
                                    type="button"
                                    onClick={() => setIsModalOpen(false)}
                                    disabled={saving}
                                    className="px-5 py-3 bg-gray-800 hover:bg-gray-700 rounded-lg"
                                >
                                    Cancel
                                </button>

                                <button
                                    type="submit"
                                    disabled={saving}
                                    className="px-5 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    {saving ? "Saving…" : isEditMode ? "Update Issue" : "Create Issue"}
                                </button>
                            </div>

                        </form>
                    </div>
                </div>
            )}

            {isViewModalOpen && selectedIssue && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
                    <div className="bg-gray-900 border border-gray-800 rounded-2xl w-full max-w-4xl max-h-[90vh] overflow-y-auto">

                        <div className="flex justify-between items-start p-6 border-b border-gray-800">
                            <div>
                                <p className="text-sm text-gray-500">
                                    Issue #{selectedIssue.id}
                                </p>

                                <h2 className="text-2xl font-bold mt-1">
                                    {selectedIssue.title}
                                </h2>
                            </div>

                            <button
                                onClick={() => setIsViewModalOpen(false)}
                                className="text-gray-400 hover:text-white text-xl"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="p-6">

                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">

                                <div className="bg-gray-800 rounded-lg p-4">
                                    <p className="text-xs text-gray-500 mb-2">Status</p>
                                    <span className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusClass(selectedIssue.status)}`}>
                                        {selectedIssue.status?.replaceAll("_", " ")}
                                    </span>
                                </div>

                                <div className="bg-gray-800 rounded-lg p-4">
                                    <p className="text-xs text-gray-500 mb-2">Priority</p>
                                    <span className={`px-3 py-1 rounded-full text-xs font-medium ${getPriorityClass(selectedIssue.priority)}`}>
                                        {selectedIssue.priority}
                                    </span>
                                </div>

                                <div className="bg-gray-800 rounded-lg p-4">
                                    <p className="text-xs text-gray-500 mb-2">Assignee</p>
                                    <div className="flex items-center gap-2"><Avatar name={selectedIssue.assignedUser?.name || "Unassigned"} src={selectedIssue.assignedUser?.avatarUrl} size={28} /><p className="text-sm text-white">{selectedIssue.assignedUser?.name || "Unassigned"}</p></div>
                                </div>

                                <div className="bg-gray-800 rounded-lg p-4">
                                    <p className="text-xs text-gray-500 mb-2">Sprint</p>
                                    <p className="text-sm text-white">
                                        {selectedIssue.sprint?.name || "No Sprint"}
                                    </p>
                                </div>
                                <div className="bg-gray-800 rounded-lg p-4"><p className="text-xs text-gray-500 mb-2">Reported by</p><div className="flex items-center gap-2"><Avatar name={selectedIssue.reportedBy?.name || selectedIssue.reporter?.name || "Unknown"} src={selectedIssue.reportedBy?.avatarUrl || selectedIssue.reporter?.avatarUrl} size={28} /><p className="text-sm text-white">{selectedIssue.reportedBy?.name || selectedIssue.reporter?.name || "—"}</p></div></div>
                                <div className="bg-gray-800 rounded-lg p-4"><p className="text-xs text-gray-500 mb-2">Tester</p><div className="flex items-center gap-2"><Avatar name={selectedIssue.tester?.name || selectedIssue.testerName || "Unassigned"} src={selectedIssue.tester?.avatarUrl} size={28} /><p className="text-sm text-white">{selectedIssue.tester?.name || selectedIssue.testerName || testers.find(tester => String(tester.id) === String(selectedIssue.tester?.id ?? selectedIssue.testerId))?.name || "Unassigned"}</p></div></div>
                                <div className="bg-gray-800 rounded-lg p-4"><p className="text-xs text-gray-500 mb-2">Created / updated</p><p className="text-sm text-white">{selectedIssue.createdAt ? new Date(selectedIssue.createdAt).toLocaleDateString() : "—"} / {selectedIssue.updatedAt ? new Date(selectedIssue.updatedAt).toLocaleDateString() : "—"}</p></div>

                            </div>

                            <div className="mb-6 flex flex-wrap gap-2">
                                {currentUser?.role === "DEVELOPER" && String(selectedIssue.assignedUser?.id) === String(currentUser.id) && ({OPEN:["IN_PROGRESS"],IN_PROGRESS:["CODE_REVIEW"],CODE_REVIEW:["READY_FOR_TESTING"]}[selectedIssue.status] || []).map(status => <button key={status} disabled={updatingStatusId === selectedIssue.id} onClick={() => updateStatus(selectedIssue,status)} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{updatingStatusId === selectedIssue.id ? "Saving…" : status === "IN_PROGRESS" ? "Start Work" : `Move to ${status.replaceAll("_"," ")}`}</button>)}
                                {canManage && (selectedIssue.status === "OPEN" ? ["IN_PROGRESS"] : selectedIssue.status === "IN_PROGRESS" ? ["CODE_REVIEW"] : selectedIssue.status === "CODE_REVIEW" ? ["READY_FOR_TESTING"] : []).map(status => <button key={status} disabled={updatingStatusId === selectedIssue.id} onClick={() => updateStatus(selectedIssue,status)} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{updatingStatusId === selectedIssue.id ? "Saving…" : `Move to ${status.replaceAll("_"," ")}`}</button>)}
                                {(currentUser?.role === "ADMIN" || (currentUser?.role === "TESTER" && String(selectedIssue.tester?.id) === String(currentUser.id))) && ["READY_FOR_TESTING", "TESTING"].includes(selectedIssue.status) && <button onClick={() => window.location.assign(`/testing/${selectedIssue.id}`)} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white">Open Testing Workspace</button>}
                            </div>

                            <div className="mb-8">
                                <h3 className="text-lg font-semibold mb-3">
                                    Description
                                </h3>

                                <div className="bg-gray-800 rounded-lg p-4 text-gray-300 whitespace-pre-wrap">
                                    {selectedIssue.description || "No description provided."}
                                </div>
                            </div>

                            <div className="mb-8">
                                <h3 className="text-lg font-semibold mb-4">
                                    Comments
                                </h3>

                                <div className="space-y-3 mb-4">
                                    {comments.length === 0 ? (
                                        <p className="text-gray-500 text-sm">
                                            No comments yet.
                                        </p>
                                    ) : (
                                        comments.map((comment) => (
                                            <div
                                                key={comment.id}
                                                className="bg-gray-800 rounded-lg p-4"
                                            >
                                                <div className="flex items-start justify-between gap-4"><div className="flex items-center gap-3"><Avatar name={comment.user?.name || "Unknown User"} src={comment.user?.avatarUrl} size={32} /><div><p className="text-sm font-semibold text-white">{comment.user?.name || "Unknown User"}</p><p className="mt-1 text-xs text-gray-500">{formatDate(comment.createdAt)}</p></div></div>{currentUser && canManage && comment.user?.id === currentUser.id && <button onClick={() => handleDeleteComment(comment.id)} disabled={deletingCommentId === comment.id} className="text-xs text-red-400 hover:text-red-300 disabled:opacity-50">{deletingCommentId === comment.id ? "Deleting…" : "Delete"}</button>}</div>

                                                <p className="text-gray-300 text-sm mt-3 whitespace-pre-wrap">
                                                    {comment.content}
                                                </p>
                                            </div>
                                        ))
                                    )}
                                </div>

                                {["ADMIN", "PROJECT_MANAGER", "DEVELOPER"].includes(currentUser?.role) && <div className="flex gap-3">
                                    <textarea
                                        value={commentText}
                                        onChange={(e) => setCommentText(e.target.value)}
                                        onKeyDown={handleCommentKeyDown}
                                        placeholder="Write a comment... Press Enter to submit"
                                        rows="2"
                                        className="flex-1 px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                                    />

                                    <button
                                        onClick={handleAddComment}
                                        disabled={!commentText.trim() || savingComment}
                                        className="px-5 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-700 disabled:text-gray-500 rounded-lg font-semibold self-end"
                                    >
                                        {savingComment ? "Adding…" : "Add"}
                                    </button>
                                </div>}
                            </div>

                            <div>
                                <div className="flex justify-between items-center mb-4">
                                    <div>
                                        <h3 className="text-lg font-semibold">
                                            Activity
                                        </h3>
                                        <p className="text-sm text-gray-500">
                                            History of changes to this issue
                                        </p>
                                    </div>
                                </div>

                                {activityLoading ? (
                                    <div className="rounded-lg bg-gray-800 px-4 py-6 text-center text-sm text-gray-400" role="status">
                                        Loading issue activity…
                                    </div>
                                ) : activityError ? (
                                    <div className="rounded-lg border border-red-500/20 bg-red-500/10 p-4 text-sm text-red-300" role="alert">
                                        <p>{activityError}</p>
                                        <button type="button" onClick={() => fetchIssueActivity(selectedIssue.id)} className="mt-3 text-red-200 underline hover:text-white">
                                            Try again
                                        </button>
                                    </div>
                                ) : issueActivity.length === 0 ? (
                                    <div className="bg-gray-800 rounded-lg p-5 text-center">
                                        <p className="text-gray-500 text-sm">
                                            No activity recorded yet.
                                        </p>
                                    </div>
                                ) : (
                                    <div className="space-y-4">
                                        {issueActivity.map((log) => (
                                            <div
                                                key={log.id}
                                                className="flex gap-4"
                                            >
                                                <div className="flex flex-col items-center">
                                                    <div className="w-3 h-3 bg-blue-500 rounded-full mt-2"></div>
                                                    <div className="w-px bg-gray-700 flex-1 mt-2"></div>
                                                </div>

                                                <div className="bg-gray-800 rounded-lg p-4 flex-1 mb-1"><div className="flex items-start justify-between gap-4"><div className="flex items-center gap-3"><Avatar name={log.actorName || "Unknown user"} src={log.actorAvatarUrl} size={32} /><p className="text-white font-medium">{formatAction(log.action)}</p></div>

                                                        <span className="text-xs text-gray-500 whitespace-nowrap">
                                                            {formatDate(log.createdAt)}
                                                        </span>
                                                    </div>

                                                    <p className="text-gray-400 text-sm mt-2">
                                                        {log.details}
                                                    </p>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default Issues;
