import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { responseErrorMessage } from "../utils/responseError";
import { isDateOnlyOverdue, utcDateKey } from "../utils/dateOnly.js";
import { apiUrl, listIssues, createIssue, updateIssue, deleteIssue, getIssueTimeline, getIssue, addIssueDependency, removeIssueDependency } from "../services/issueApi";
import Avatar from "../components/Avatar";
import RecordTable from "../components/RecordTable";
import { loadDropdownOptions, dropdownLabel } from "../services/dropdownOptions";

const CANONICAL_STATUSES = ["OPEN", "IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING", "TESTING", "TEAM_LEAD_APPROVAL", "PROJECT_MANAGER_APPROVAL", "RESOLVED"];

function Issues() {
    const token = localStorage.getItem("token");
    const [searchParams, setSearchParams] = useSearchParams();
    const handleViewIssueRef = useRef(null);

    const [issues, setIssues] = useState([]);
    const [priorityOptions, setPriorityOptions] = useState([
        { code: "CRITICAL", displayLabel: "Critical" }, { code: "HIGH", displayLabel: "High" },
        { code: "MEDIUM", displayLabel: "Medium" }, { code: "LOW", displayLabel: "Low" }
    ]);
    const [issueStatusOptions, setIssueStatusOptions] = useState(CANONICAL_STATUSES.map(code => ({ code, displayLabel: code.replaceAll("_", " ") })));
    const statusLabel = status => dropdownLabel(issueStatusOptions, status, String(status || "").replaceAll("_", " "));
    const [projects, setProjects] = useState([]);
    const [users, setUsers] = useState([]);
    const [testers, setTesters] = useState([]);
    const [projectAssignableMembers, setProjectAssignableMembers] = useState([]);
    const [projectAssignableMembersFor, setProjectAssignableMembersFor] = useState("");
    const [projectMembersLoading, setProjectMembersLoading] = useState(false);
    const [projectMembersError, setProjectMembersError] = useState("");
    const [testersLoading, setTestersLoading] = useState(false);
    const [sprints, setSprints] = useState([]);
    const [currentUser, setCurrentUser] = useState(null);
    const [effectivePermissions, setEffectivePermissions] = useState(null);

    const [selectedIssue, setSelectedIssue] = useState(null);
    const [comments, setComments] = useState([]);
    const [issueActivity, setIssueActivity] = useState([]);
    const [approvalHistory, setApprovalHistory] = useState([]);
    const [approvalLoading, setApprovalLoading] = useState(false);
    const [approvalError, setApprovalError] = useState("");
    const [approvalSaving, setApprovalSaving] = useState(false);
    const [approvalRemark, setApprovalRemark] = useState("");
    const [maintenance, setMaintenance] = useState(null);
    const [maintenanceLoading, setMaintenanceLoading] = useState(false);
    const [maintenanceError, setMaintenanceError] = useState("");
    const [maintenanceSaving, setMaintenanceSaving] = useState(false);
    const [maintenanceTitle, setMaintenanceTitle] = useState("");
    const [maintenanceReason, setMaintenanceReason] = useState("");
    const [maintenanceDeveloperId, setMaintenanceDeveloperId] = useState("");
    const [activityLoading, setActivityLoading] = useState(false);
    const [activityError, setActivityError] = useState("");
    const [dependencyLoading, setDependencyLoading] = useState(false);
    const [dependencySaving, setDependencySaving] = useState(false);
    const [dependencyError, setDependencyError] = useState("");
    const [dependencyNotice, setDependencyNotice] = useState("");
    const [blockingIssueId, setBlockingIssueId] = useState("");
    const [blockingReason, setBlockingReason] = useState("");
    const [commentText, setCommentText] = useState("");

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);
    const [isEditMode, setIsEditMode] = useState(false);
    const [editAction, setEditAction] = useState("DETAILS");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [priorityFilter, setPriorityFilter] = useState("ALL");
    const [projectFilter, setProjectFilter] = useState("ALL");

    const [formData, setFormData] = useState({
        title: "",
        description: "",
        acceptanceCriteria: "",
        dueDate: "",
        priority: "MEDIUM",
        status: "OPEN",
        projectId: "",
        assignedUserId: "",
        sprintId: ""
    });

    const priorityFilterOptions = [...priorityOptions];
    const knownPriorityCodes = new Set(priorityFilterOptions.map(option => option.code));
    for (const issue of issues) {
        const code = String(issue.priority || "").trim().toUpperCase();
        if (code && !knownPriorityCodes.has(code)) {
            priorityFilterOptions.push({ code, displayLabel: `${code.replaceAll("_", " ")} (existing value)` });
            knownPriorityCodes.add(code);
        }
    }
    const formPriorityOptions = [...priorityOptions];
    const existingPriorityCode = String(selectedIssue?.priority || "").trim().toUpperCase();
    if (isEditMode && existingPriorityCode && !formPriorityOptions.some(option => option.code === existingPriorityCode)) {
        formPriorityOptions.push({ code: existingPriorityCode, displayLabel: `${existingPriorityCode.replaceAll("_", " ")} (disabled existing value)`, enabled: false });
    }

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [updatingStatusId, setUpdatingStatusId] = useState(null);
    const [savingComment, setSavingComment] = useState(false);
    const [deletingCommentId, setDeletingCommentId] = useState(null);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const selectedProjectAssignableMembers = String(projectAssignableMembersFor) === String(formData.projectId)
        ? projectAssignableMembers : [];

    const authHeaders = useMemo(() => ({
        Authorization: `Bearer ${token}`
    }), [token]);

    useEffect(() => {
        let active = true;
        fetch(apiUrl("/api/config/dropdowns/PRIORITY"), { headers: authHeaders })
            .then(response => response.ok ? response.json() : null)
            .then(data => { if (active && Array.isArray(data) && data.length) setPriorityOptions(data); })
            .catch(() => {});
        loadDropdownOptions("ISSUE_STATUS").then(data => { if (active && data.length) setIssueStatusOptions(data); }).catch(() => {});
        return () => { active = false; };
    }, [authHeaders]);

    const loadProjectAssignableMembers = useCallback(projectId => {
        if (!projectId) {
            setProjectAssignableMembers([]); setProjectAssignableMembersFor("");
            setProjectMembersError(""); setProjectMembersLoading(false);
            return;
        }
        setProjectMembersLoading(true); setProjectMembersError("");
        fetch(apiUrl(`/api/projects/${projectId}/assignable-members`), { headers: authHeaders })
            .then(async response => { if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not load project team responsibilities.")); return response.json(); })
            .then(data => { setProjectAssignableMembers(Array.isArray(data) ? data : []); setProjectAssignableMembersFor(String(projectId)); })
            .catch(error => { setProjectAssignableMembers([]); setProjectAssignableMembersFor(""); setProjectMembersError(error.message); })
            .finally(() => setProjectMembersLoading(false));
    }, [authHeaders]);

    useEffect(() => {
        let active = true;
        fetch(apiUrl("/api/users/me/permissions"), { headers: authHeaders })
            .then(response => response.ok ? response.json() : null)
            .then(data => { if (active) setEffectivePermissions(data); })
            .catch(() => { if (active) setEffectivePermissions(null); });
        return () => { active = false; };
    }, [authHeaders]);

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
                throw new Error("The tasks endpoint returned an unexpected response format.");
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
                } finally {
                    setTestersLoading(false);
                }
            } else {
                setTesters([]);
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
            const entries = await getIssueTimeline(token, issueId);
            if (!Array.isArray(entries)) throw new Error("Task activity returned an unexpected response format.");
            setIssueActivity(entries);
        } catch (error) {
            setIssueActivity([]);
            setActivityError(error.message);
        } finally {
            setActivityLoading(false);
        }
    };
    const handleInputChange = (e) => {
        const { name, value } = e.target;
        if (name === "projectId") loadProjectAssignableMembers(value);
        setFormData(current => ({
            ...current,
            [name]: value,
            ...(name === "projectId" && current.sprintId && !sprints.some(sprint =>
                String(sprint.id) === String(current.sprintId)
                && String(sprint.project?.id ?? sprint.projectId ?? "") === String(value)
            ) ? { sprintId: "" } : {})
        }));
    };

    const resetForm = () => {
        setFormData({
            title: "",
            description: "",
            acceptanceCriteria: "",
        dueDate: "",
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
        setEditAction("CREATE");
        setIsModalOpen(true);
    };

    const openEditModal = (issue, action = "DETAILS") => {
        setFormData({
            title: issue.title || "",
            description: issue.description || "",
            acceptanceCriteria: issue.acceptanceCriteria || "",
            dueDate: issue.dueDate || "",
            priority: issue.priority || "MEDIUM",
            status: issue.status || "OPEN",
            projectId: issue.project?.id ?? issue.projectId ?? "",
            assignedUserId: issue.assignedUser?.id ?? issue.assignedUserId ?? "",
            sprintId: issue.sprint?.id ?? issue.sprintId ?? "",
            testerId: issue.tester?.id ?? issue.testerId ?? ""
        });

        setSelectedIssue(issue);
        setIsEditMode(true);
        setEditAction(action);
        setIsModalOpen(true);
        loadProjectAssignableMembers(issue.project?.id ?? issue.projectId);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSaving(true);
        setError("");
        setNotice("");

        try {
            let issueData = {};
            let savedIssue;
            if (isEditMode) {
                const previousProjectId = selectedIssue?.project?.id ?? selectedIssue?.projectId ?? "";
                const previousDeveloperId = selectedIssue?.assignedUser?.id ?? selectedIssue?.assignedUserId ?? "";
                const previousSprintId = selectedIssue?.sprint?.id ?? selectedIssue?.sprintId ?? "";
                const previousTesterId = selectedIssue?.tester?.id ?? selectedIssue?.testerId ?? "";
                const sameId = (next, previous) => String(next ?? "") === String(previous ?? "");
                if (editAction === "DETAILS") {
                    if (formData.title.trim() !== (selectedIssue?.title ?? "")) issueData.title = formData.title.trim();
                    if (formData.description !== (selectedIssue?.description ?? "")) issueData.description = formData.description;
                    if (formData.acceptanceCriteria !== (selectedIssue?.acceptanceCriteria ?? "")) issueData.acceptanceCriteria = formData.acceptanceCriteria;
                } else if (editAction === "DUE_DATE" && formData.dueDate !== (selectedIssue?.dueDate ?? "")) {
                    issueData.dueDate = formData.dueDate || null;
                } else if (editAction === "STATUS" && formData.status !== selectedIssue?.status) {
                    issueData.status = formData.status;
                } else if (editAction === "PRIORITY" && formData.priority !== selectedIssue?.priority) {
                    issueData.priority = formData.priority;
                } else if (editAction === "ASSIGNEE" && canManage && !sameId(formData.assignedUserId, previousDeveloperId)) {
                    issueData.assignedUser = formData.assignedUserId ? { id: Number(formData.assignedUserId) } : null;
                } else if (editAction === "TESTER" && canManage && !sameId(formData.testerId, previousTesterId)) {
                    issueData.tester = formData.testerId ? { id: Number(formData.testerId) } : null;
                } else if (editAction === "PLANNING" && canManage) {
                    const projectChanged = !sameId(formData.projectId, previousProjectId);
                    if (projectChanged) issueData.project = formData.projectId ? { id: Number(formData.projectId) } : null;
                    if (!sameId(formData.sprintId, previousSprintId) || projectChanged) {
                        issueData.sprint = formData.sprintId ? { id: Number(formData.sprintId) } : null;
                    }
                }
                if (Object.keys(issueData).length === 0) {
                    setNotice("No changes to save.");
                    setIsModalOpen(false);
                    return;
                }
                savedIssue = await updateIssue(token, selectedIssue.id, issueData);
            } else {
                issueData = {
                    title: formData.title.trim(),
                    description: formData.description,
                    acceptanceCriteria: formData.acceptanceCriteria,
                    dueDate: formData.dueDate || null,
                    priority: formData.priority,
                    status: formData.status,
                    project: formData.projectId ? { id: Number(formData.projectId) } : null
                };
                if (canManage && formData.assignedUserId) issueData.assignedUser = { id: Number(formData.assignedUserId) };
                if (canManage && formData.sprintId) issueData.sprint = { id: Number(formData.sprintId) };
                if (canManage && formData.testerId) issueData.tester = { id: Number(formData.testerId) };
                savedIssue = await createIssue(token, issueData);
            }

            try {
                const refreshedIssues = await listIssues(token);
                if (!Array.isArray(refreshedIssues)) throw new Error("Unexpected task list response.");
                setIssues(refreshedIssues);
            } catch {
                setIssues(previous => previous.some(issue => String(issue.id) === String(savedIssue.id))
                    ? previous.map(issue => String(issue.id) === String(savedIssue.id) ? savedIssue : issue)
                    : [...previous, savedIssue]);
                setError("The task was saved, but the list could not be refreshed. Reload to see the latest data.");
            }

            setIsModalOpen(false);
            setSelectedIssue(null);
            resetForm();
            setNotice(isEditMode ? "Task updated successfully." : "Task created successfully.");
        } catch (error) {
            setError(error.message);
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm("Are you sure you want to delete this task?")) return;

        try {
            setDeletingId(id);
            setError("");
            setNotice("");
            await deleteIssue(token, id);

            setIssues((prev) => prev.filter((issue) => issue.id !== id));
            setNotice("Task deleted successfully.");
        } catch (error) {
            setError(error.message);
        } finally {
            setDeletingId(null);
        }
    };

    const loadApprovalHistory = async issueId => {
        setApprovalLoading(true); setApprovalError("");
        try {
            const response = await fetch(apiUrl(`/api/issues/${issueId}/approvals`), { headers: authHeaders });
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not load approval history."));
            const data = await response.json(); setApprovalHistory(Array.isArray(data) ? data : []);
        } catch (requestError) { setApprovalHistory([]); setApprovalError(requestError.message || "Could not load approval history."); }
        finally { setApprovalLoading(false); }
    };
    const loadMaintenance = async issueId => {
        setMaintenanceLoading(true); setMaintenanceError("");
        try {
            const response = await fetch(apiUrl(`/api/issues/${issueId}/maintenance`), { headers: authHeaders });
            if (response.status === 204) { setMaintenance(null); return; }
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not load maintenance history."));
            setMaintenance(await response.json());
        } catch (requestError) { setMaintenance(null); setMaintenanceError(requestError.message || "Could not load maintenance history."); }
        finally { setMaintenanceLoading(false); }
    };
    const loadWorkflowDetails = issue => {
        void loadApprovalHistory(issue.id);
        void loadMaintenance(issue.id);
        const projectId = issue.project?.id ?? issue.projectId;
        loadProjectAssignableMembers(projectId);
    };
    const saveApprovalDecision = async (stage, decision) => {
        if (!selectedIssue) return;
        if (decision === "REJECTED" && !approvalRemark.trim()) { setApprovalError("A remark is required when rejecting this task."); return; }
        setApprovalSaving(true); setApprovalError("");
        try {
            const response = await fetch(apiUrl(`/api/issues/${selectedIssue.id}/approvals/${stage}`), {
                method: "POST", headers: { ...authHeaders, "Content-Type": "application/json" },
                body: JSON.stringify({ decision, ...(decision === "REJECTED" ? { remark: approvalRemark.trim() } : {}) })
            });
            if (!response.ok) throw new Error(await responseErrorMessage(response, "The approval decision could not be saved."));
            setApprovalRemark("");
            const latest = await getIssue(token, selectedIssue.id);
            setSelectedIssue(latest); setIssues(current => current.map(item => item.id === latest.id ? latest : item));
            await Promise.all([loadApprovalHistory(latest.id), loadMaintenance(latest.id), fetchIssueActivity(latest.id)]);
        } catch (requestError) { setApprovalError(requestError.message || "The approval decision could not be saved."); }
        finally { setApprovalSaving(false); }
    };
    const createMaintenanceFollowUp = async event => {
        event.preventDefault();
        if (!selectedIssue || !maintenanceReason.trim() || !maintenanceDeveloperId) return;
        setMaintenanceSaving(true); setMaintenanceError("");
        try {
            const response = await fetch(apiUrl(`/api/issues/${selectedIssue.id}/maintenance-follow-ups`), {
                method: "POST", headers: { ...authHeaders, "Content-Type": "application/json" },
                body: JSON.stringify({ title: maintenanceTitle.trim() || undefined, reason: maintenanceReason.trim(), responsibleDeveloperId: Number(maintenanceDeveloperId) })
            });
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not create maintenance follow-up."));
            setMaintenanceTitle(""); setMaintenanceReason(""); setMaintenanceDeveloperId("");
            await Promise.all([loadMaintenance(selectedIssue.id), fetchIssueActivity(selectedIssue.id)]);
        } catch (requestError) { setMaintenanceError(requestError.message || "Could not create maintenance follow-up."); }
        finally { setMaintenanceSaving(false); }
    };

    const handleViewIssue = async (issue) => {
        setSelectedIssue(issue);
        setIsViewModalOpen(true);
        setIsEditMode(false);
        setComments([]);
        setIssueActivity([]);
        setActivityError("");
        setApprovalHistory([]); setApprovalError(""); setApprovalRemark("");
        setMaintenance(null); setMaintenanceError(""); setMaintenanceTitle(""); setMaintenanceReason(""); setMaintenanceDeveloperId("");
        setDependencyError("");
        setDependencyNotice("");
        setDependencyLoading(true);
        setCommentText("");

        void fetchComments(issue.id);
        void fetchIssueActivity(issue.id);
        loadWorkflowDetails(issue);
        try {
            const latestIssue = await getIssue(token, issue.id);
            setSelectedIssue(latestIssue);
            setIssues(current => current.map(item => item.id === latestIssue.id ? latestIssue : item));
        } catch (requestError) {
            setDependencyError(requestError.message || "Could not refresh this task.");
        } finally {
            setDependencyLoading(false);
        }
    };

    useEffect(() => {
        handleViewIssueRef.current = handleViewIssue;
    });

    useEffect(() => {
        const requestedIssueId = searchParams.get("issueId");
        if (!requestedIssueId || loading) return;
        const issue = issues.find(item => String(item.id) === String(requestedIssueId));
        if (!issue) return;
        setSearchParams(current => {
            const next = new URLSearchParams(current);
            next.delete("issueId");
            return next;
        }, { replace: true });
        void handleViewIssueRef.current?.(issue);
    }, [issues, loading, searchParams, setSearchParams]);

    const canManage = ["ADMIN", "PROJECT_MANAGER"].includes(currentUser?.role);
    const sprintsForProject = formData.projectId
        ? sprints.filter(sprint => String(sprint.project?.id ?? sprint.projectId ?? "") === String(formData.projectId))
        : [];
    const filteredIssues = issues.filter(issue =>
        (statusFilter === "ALL" || issue.status === statusFilter)
        && (priorityFilter === "ALL" || issue.priority === priorityFilter)
        && (projectFilter === "ALL" || String(issue.project?.id ?? issue.projectId ?? "") === projectFilter)
    );
    const refreshSelectedIssue = async () => {
        if (!selectedIssue) return;
        setDependencyLoading(true);
        setDependencyError("");
        setDependencyNotice("");
        try {
            const latestIssue = await getIssue(token, selectedIssue.id);
            setSelectedIssue(latestIssue);
            setIssues(current => current.map(item => item.id === latestIssue.id ? latestIssue : item));
        } catch (requestError) {
            setDependencyError(requestError.message || "Could not refresh dependency status.");
        } finally {
            setDependencyLoading(false);
        }
    };    const handleAddDependency = async event => {
        event.preventDefault();
        if (!selectedIssue || !blockingIssueId || !blockingReason.trim()) return;
        setDependencySaving(true);
        setDependencyError("");
        setDependencyNotice("");
        try {
            const updated = await addIssueDependency(token, selectedIssue.id, Number(blockingIssueId), blockingReason.trim());
            setSelectedIssue(updated);
            setIssues(current => current.map(item => item.id === updated.id ? updated : item));
            setBlockingIssueId("");
            setBlockingReason("");
            setDependencyNotice("Task blocker added.");
        } catch (requestError) {
            setDependencyError(requestError.message || "Could not add task blocker.");
        } finally {
            setDependencySaving(false);
        }
    };
    const handleRemoveDependency = async relatedIssueId => {
        if (!selectedIssue) return;
        setDependencySaving(true);
        setDependencyError("");
        setDependencyNotice("");
        try {
            await removeIssueDependency(token, selectedIssue.id, relatedIssueId);
            const updated = await getIssue(token, selectedIssue.id);
            setSelectedIssue(updated);
            setIssues(current => current.map(item => item.id === updated.id ? updated : item));
            setDependencyNotice("Task blocker removed.");
        } catch (requestError) {
            setDependencyError(requestError.message || "Could not remove task blocker.");
        } finally {
            setDependencySaving(false);
        }
    };
    const updateStatus = async (issue, status) => {
        setUpdatingStatusId(issue.id);
        setError("");
        setNotice("");
        try {
            const updated = await updateIssue(token, issue.id, { status }); setIssues(old => old.map(item => item.id === updated.id ? updated : item));
            setSelectedIssue(updated);
            fetchIssueActivity(issue.id);
            setNotice("Task status updated successfully.");
        } catch (e) { setError(e.message); }
        finally { setUpdatingStatusId(null); }
    };
    const canEditTask = task => canManage
        || (currentUser?.role === "DEVELOPER" && String(task.assignedUser?.id) === String(currentUser.id));
    const hasProjectPermission = (permission, projectId) => Boolean(
        effectivePermissions?.global?.includes(permission)
        || effectivePermissions?.projects?.some(scope => String(scope.scopeId) === String(projectId) && scope.permissions?.includes(permission))
    );
    const canApproveTeamLead = selectedIssue && hasProjectPermission("ISSUE_APPROVE_TEAM_LEAD", selectedIssue.project?.id ?? selectedIssue.projectId);
    const canApproveProjectManager = selectedIssue && hasProjectPermission("ISSUE_APPROVE_PROJECT_MANAGER", selectedIssue.project?.id ?? selectedIssue.projectId);
    const inlineStatusOptions = task => {
        if (canManage) return CANONICAL_STATUSES.filter(status => !["TESTING", "TEAM_LEAD_APPROVAL", "PROJECT_MANAGER_APPROVAL", "RESOLVED"].includes(status) || status === task.status);
        const permitted = { OPEN: ["IN_PROGRESS"], IN_PROGRESS: ["CODE_REVIEW"], CODE_REVIEW: ["READY_FOR_TESTING"] }[task.status] || [];
        return [task.status, ...permitted].filter(Boolean);
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
        const labels = {
            ISSUE_CREATED: "Task created", STATUS_CHANGED: "Status changed",
            ASSIGNED_USER_CHANGED: "Developer assigned", TESTER_ASSIGNED: "Tester assigned",
            DUE_DATE_CHANGED: "Due date changed", GITHUB_ISSUE_CREATED: "External issue link recorded",
            GITHUB_ISSUE_RECONCILED: "External issue link recovered", ISSUE_BLOCKER_ADDED: "Blocker added",
            ISSUE_BLOCKER_REMOVED: "Blocker removed", COMMENT_ADDED: "Comment added",
            COMMENT_DELETED: "Comment deleted"
        };
        return labels[action] || (action ? action.replaceAll("_", " ") : "Activity");
    };

    const isTaskOverdue = (task) => isDateOnlyOverdue(task.dueDate, task.status, utcDateKey(new Date()));

    const formatDate = (date) => {
        if (!date) return "";
        return new Date(date).toLocaleString();
    };

    const getTaskTitle = task => {
        const title = task?.title?.trim();
        if (!title || /^(?:issue|task)\s*#?\s*\d+$/i.test(title)) {
            const descriptionTitle = task?.description?.split(/\r?\n/).find(line => line.trim())?.trim();
            return descriptionTitle ? descriptionTitle.slice(0, 96) : "Untitled task";
        }
        return title;
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-950 flex items-center justify-center">
                <p className="text-gray-400">Loading tasks...</p>
            </div>
        );
    }

    return (
        <div className="dfx-screen dfx-screen--tasks min-h-screen bg-gray-950 text-white p-6">
            <div className="max-w-7xl mx-auto">

                <div className="dfx-screen-header flex justify-between items-center mb-8">
                    <div>
                        <p className="mb-2 text-xs font-semibold uppercase tracking-[.16em] text-blue-300">ENGINEERING / WORK ITEMS</p><h1 className="text-3xl font-bold">Tasks</h1>
                        <p className="text-gray-400 mt-1">
                            Manage and track project tasks
                        </p>
                    </div>

                    {["ADMIN", "PROJECT_MANAGER", "DEVELOPER"].includes(currentUser?.role) && <button
                        onClick={openCreateModal}
                        className="px-5 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg font-semibold transition"
                    >
                        + Create Task
                    </button>}
                </div>

                <div className="dfx-task-overview" aria-label="Task overview">
                    <article><span>All tasks</span><strong>{issues.length}</strong><small>Tracked work items</small></article>
                    <article><span>In progress</span><strong>{issues.filter(issue => ["IN_PROGRESS", "CODE_REVIEW"].includes(issue.status)).length}</strong><small>Development or code review</small></article>
                    <article><span>Testing</span><strong>{issues.filter(issue => ["READY_FOR_TESTING", "TESTING"].includes(issue.status)).length}</strong><small>Ready or currently testing</small></article>
                    <article><span>High priority</span><strong>{issues.filter(issue => ["HIGH", "CRITICAL"].includes(issue.priority)).length}</strong><small>High or critical priority</small></article>
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

                <div className="dfx-task-filters" aria-label="Filter tasks">
                    <label>Status<select aria-label="Filter tasks by status" value={statusFilter} onChange={event => setStatusFilter(event.target.value)}><option value="ALL">All statuses</option>{[...new Set([...CANONICAL_STATUSES, ...issues.map(issue => issue.status).filter(Boolean)])].map(status => <option key={status} value={status}>{statusLabel(status)}</option>)}</select></label>
                    <label>Priority<select aria-label="Filter tasks by priority" value={priorityFilter} onChange={event => setPriorityFilter(event.target.value)}><option value="ALL">All priorities</option>{priorityFilterOptions.map(option => <option key={option.code} value={option.code}>{option.displayLabel}</option>)}</select></label>
                    <label>Project<select aria-label="Filter tasks by project" value={projectFilter} onChange={event => setProjectFilter(event.target.value)}><option value="ALL">All projects</option>{projects.map(project => <option key={project.id} value={String(project.id)}>{project.name}</option>)}</select></label>
                    {(statusFilter !== "ALL" || priorityFilter !== "ALL" || projectFilter !== "ALL") && <button type="button" onClick={() => { setStatusFilter("ALL"); setPriorityFilter("ALL"); setProjectFilter("ALL"); }} className="df-button-secondary px-3 py-2 text-sm">Clear filters</button>}
                </div>

                {issues.length === 0 && !error ? (
                    <div className="bg-gray-900 border border-gray-800 rounded-xl p-12 text-center">
                        <h2 className="text-xl font-semibold">No tasks found</h2>
                        <p className="text-gray-400 mt-2">
                            Create your first task to get started.
                        </p>
                    </div>
                ) : (
                    <div className="dfx-task-table-panel df-panel overflow-hidden">
                        <RecordTable rows={filteredIssues} searchLabel="Search tasks" searchPlaceholder="Search by task, ID, project, assignee, or status…" emptyLabel="No tasks match these filters." getSearchText={task => `${getTaskTitle(task)} ${task.id} ${task.description || ""} ${task.project?.name || ""} ${task.assignedUser?.name || ""} ${task.sprint?.name || ""} ${task.status || ""} ${task.priority || ""}`} columns={[
                            { key: "title", label: "Task", sortValue: task => getTaskTitle(task), render: task => <div className="min-w-48"><button type="button" onClick={() => handleViewIssue(task)} className="text-left font-semibold text-white hover:text-blue-300">{getTaskTitle(task)}</button><p className="mt-1 text-xs text-gray-500">Task #{task.id}</p>{task.blocked && <span className="mt-2 inline-flex rounded-full border border-amber-400/30 bg-amber-400/10 px-2 py-1 text-[10px] font-semibold tracking-wide text-amber-300">BLOCKED</span>}</div> },
                            { key: "project", label: "Project", sortValue: task => task.project?.name || "", render: task => task.project?.name || "No project" },
                            { key: "assignee", label: "Assignee", sortValue: task => task.assignedUser?.name || "", render: task => task.assignedUser?.name || "Unassigned" },
                            { key: "sprint", label: "Sprint", sortValue: task => task.sprint?.name || "", render: task => task.sprint?.name || "No sprint" },
                            { key: "dueDate", label: "Due date", sortValue: task => task.dueDate || "", render: task => task.dueDate ? <div><span>{new Date(`${task.dueDate}T00:00:00`).toLocaleDateString()}</span>{isTaskOverdue(task) && <span className="ml-2 rounded-full bg-red-500/15 px-2 py-1 text-[10px] font-semibold text-red-300">OVERDUE</span>}</div> : "No due date" },
                            { key: "priority", label: "Priority", sortValue: task => task.priority, render: task => <span className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium ${getPriorityClass(task.priority)}`}>{dropdownLabel(priorityOptions,task.priority,task.priority || "—")}</span> },
                            { key: "status", label: "Status", sortValue: task => task.status, render: task => <span className={`inline-flex whitespace-normal rounded-full px-2.5 py-1 text-xs font-medium ${getStatusClass(task.status)}`}>{statusLabel(task.status) || "Unknown"}</span> }
                        ]} actionsForRow={task => [
                            { label: "View details", onSelect: () => handleViewIssue(task) },
                            ...(canEditTask(task) ? [
                                { label: "Edit details", onSelect: () => openEditModal(task, "DETAILS") },
                                { label: "Change status", onSelect: () => openEditModal(task, "STATUS") },
                { label: "Change due date", onSelect: () => openEditModal(task, "DUE_DATE") },
                                { label: "Change priority", onSelect: () => openEditModal(task, "PRIORITY") }
                            ] : []),
                            ...(canManage ? [
                                { label: "Change assignee", onSelect: () => openEditModal(task, "ASSIGNEE") },
                                { label: "Assign tester", onSelect: () => openEditModal(task, "TESTER") },
                                { label: "Change project or sprint", onSelect: () => openEditModal(task, "PLANNING") },
                                { label: deletingId === task.id ? "Deleting…" : "Delete task", danger: true, disabled: deletingId === task.id, onSelect: () => handleDelete(task.id) }
                            ] : [])
                        ]} />
                    </div>
                )}
            </div>

            {isModalOpen && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
                    <div className="bg-gray-900 border border-gray-800 rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">

                        <div className="flex justify-between items-center p-6 border-b border-gray-800">
                            <div>
                                <h2 className="text-2xl font-semibold">
                                    {!isEditMode ? "Create Task" : ({ DETAILS: "Edit Task Details", DUE_DATE: "Change Due Date", ASSIGNEE: "Change Assignee", TESTER: "Assign Tester", STATUS: "Change Status", PRIORITY: "Change Priority", PLANNING: "Change Project or Sprint" }[editAction] || "Edit Task")}
                                </h2>
                                <p className="text-gray-400 text-sm mt-1">
                                    {!isEditMode ? "Create a task with the planning and assignment fields supported by this workspace." : "Only the selected task field is submitted; other values are left unchanged."}
                                </p>
                            </div>

                            <button
                                onClick={() => setIsModalOpen(false)}
                                className="text-gray-400 hover:text-white text-xl"
                            >
                                ✕
                            </button>
                        </div>

                        <form onSubmit={handleSubmit} className="dfx-task-modal-form p-6">
                            {!isEditMode ? <>
                                <section className="dfx-task-form-section">
                                    <h3>Planning</h3>
                                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                                        <div className="dfx-task-field"><label htmlFor="task-project">Project <span className="dfx-task-required">*</span></label><select id="task-project" name="projectId" value={formData.projectId} onChange={handleInputChange} required><option value="">Select a project</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select></div>
                                        <div className="dfx-task-field"><label htmlFor="task-priority">Priority</label><select id="task-priority" name="priority" value={formData.priority} onChange={handleInputChange}>{priorityOptions.map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}</select></div>
                                    </div>
                                    {canManage && <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                                        <div className="dfx-task-field"><label htmlFor="task-status">Initial status</label><select id="task-status" name="status" value={formData.status} onChange={handleInputChange}>{["OPEN", "IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING"].map(status => <option key={status} value={status}>{statusLabel(status)}</option>)}</select><small className="text-xs text-gray-500">Testing and resolution are completed through the testing workflow.</small></div>
                                        <div className="dfx-task-field"><label htmlFor="task-sprint">Sprint</label><select id="task-sprint" name="sprintId" value={formData.sprintId} onChange={handleInputChange} disabled={!formData.projectId}><option value="">No sprint</option>{sprintsForProject.map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name}</option>)}</select><small className="text-xs text-gray-500">Only sprints in the selected project are shown.</small></div>
                                    </div>}
                                </section>
                                {canManage && <section className="dfx-task-form-section">
                                    <h3>Assignment</h3>
                                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                                        <div className="dfx-task-field"><label htmlFor="task-assignee">Developer</label><select id="task-assignee" name="assignedUserId" value={formData.assignedUserId} onChange={handleInputChange} disabled={Boolean(formData.projectId) && (projectMembersLoading || String(projectAssignableMembersFor) !== String(formData.projectId))}><option value="">Unassigned</option>{(formData.projectId ? selectedProjectAssignableMembers.filter(user=>user.developer) : users.filter(user=>user.role==="DEVELOPER")).map(user=><option key={user.id} value={user.id}>{user.name}</option>)}</select>{projectMembersLoading&&<small className="text-xs text-gray-500">Loading this project’s team…</small>}</div>
                                        <div className="dfx-task-field"><label htmlFor="task-tester">Tester</label><select id="task-tester" name="testerId" value={formData.testerId ?? ""} onChange={handleInputChange} disabled={projectMembersLoading || saving || (Boolean(formData.projectId) && String(projectAssignableMembersFor) !== String(formData.projectId))}><option value="">Unassigned</option>{(formData.projectId ? selectedProjectAssignableMembers.filter(user=>user.tester) : testers).map(person=><option key={person.id} value={person.id}>{person.name}</option>)}</select>{projectMembersError&&<small role="alert" className="text-xs text-red-300">{projectMembersError}</small>}</div>
                                    </div>
                                    <p className="text-xs leading-5 text-gray-500">Project-scoped options include only enabled members holding the matching responsibility. The backend validates the same relationship when saving.</p>
                                </section>}
                                <section className="dfx-task-form-section">
                                    <h3>Basic information</h3>
                                    <div className="dfx-task-field"><label htmlFor="task-title">Task title <span className="dfx-task-required">*</span></label><input id="task-title" type="text" name="title" value={formData.title} onChange={handleInputChange} placeholder="For example, Add password reset flow" maxLength={200} required /></div>
                                    <div className="dfx-task-field"><label htmlFor="task-description">Description</label><textarea id="task-description" name="description" value={formData.description} onChange={handleInputChange} placeholder="Describe the work and relevant context" rows={4} /></div>
                                    <div className="dfx-task-field"><label htmlFor="task-acceptance">Acceptance criteria</label><textarea id="task-acceptance" name="acceptanceCriteria" value={formData.acceptanceCriteria} onChange={handleInputChange} placeholder="List the conditions that must be met" rows={3} /></div>
                                    <div className="dfx-task-field"><label htmlFor="task-due-date">Due date</label><input id="task-due-date" type="date" name="dueDate" value={formData.dueDate} onChange={handleInputChange} /><small className="text-xs text-gray-500">Optional target date for this task.</small></div>
                                </section>
                                <p className="text-xs text-gray-500">Dependencies can be linked after the task is created. Reviewer assignment is not supported by the current issue API.</p>
                            </> : editAction === "DETAILS" ? <section className="dfx-task-form-section">
                                <h3>Task details</h3>
                                <div className="dfx-task-field"><label htmlFor="task-title">Title</label><input id="task-title" name="title" value={formData.title} onChange={handleInputChange} maxLength={200} required /></div>
                                <div className="dfx-task-field"><label htmlFor="task-description">Description</label><textarea id="task-description" name="description" value={formData.description} onChange={handleInputChange} rows={4} /></div>
                                <div className="dfx-task-field"><label htmlFor="task-acceptance">Acceptance criteria</label><textarea id="task-acceptance" name="acceptanceCriteria" value={formData.acceptanceCriteria} onChange={handleInputChange} rows={3} /></div>
                            </section> : editAction === "DUE_DATE" ? <section className="dfx-task-form-section"><h3>Planning</h3><div className="dfx-task-field"><label htmlFor="task-due-date">Due date</label><input id="task-due-date" type="date" name="dueDate" value={formData.dueDate} onChange={handleInputChange} /><small className="text-xs text-gray-500">Leave blank and save to clear the current due date.</small></div></section> : editAction === "STATUS" ? <section className="dfx-task-form-section"><h3>Status</h3><div className="dfx-task-field"><label htmlFor="task-status">New status</label><select id="task-status" name="status" value={formData.status} onChange={handleInputChange}>{(canManage ? CANONICAL_STATUSES.filter(status => !["TESTING", "TEAM_LEAD_APPROVAL", "PROJECT_MANAGER_APPROVAL", "RESOLVED"].includes(status) || status === selectedIssue?.status) : inlineStatusOptions(selectedIssue)).map(status => <option key={status} value={status}>{statusLabel(status)}</option>)}</select><small className="text-xs text-gray-500">Testing, approvals and resolution are controlled by their workflow actions.</small></div></section> : editAction === "PRIORITY" ? <section className="dfx-task-form-section"><h3>Priority</h3><div className="dfx-task-field"><label htmlFor="task-priority">New priority</label><select id="task-priority" name="priority" value={formData.priority} onChange={handleInputChange}>{formPriorityOptions.map(option=><option key={option.code} value={option.code} disabled={option.enabled===false}>{option.displayLabel}</option>)}</select></div></section> : editAction === "ASSIGNEE" && canManage ? <section className="dfx-task-form-section"><h3>Assignment</h3><div className="dfx-task-field"><label htmlFor="task-assignee">Developer</label><select id="task-assignee" name="assignedUserId" value={formData.assignedUserId} onChange={handleInputChange} disabled={Boolean(formData.projectId) && (projectMembersLoading || String(projectAssignableMembersFor) !== String(formData.projectId))}><option value="">Unassigned</option>{(formData.projectId ? selectedProjectAssignableMembers.filter(user=>user.developer) : users.filter(user => user.role === "DEVELOPER")).map(user => <option key={user.id} value={user.id}>{user.name}</option>)}</select>{projectMembersError && <small role="alert" className="text-xs text-red-300">{projectMembersError}</small>}</div></section> : editAction === "TESTER" && canManage ? <section className="dfx-task-form-section"><h3>Tester</h3><div className="dfx-task-field"><label htmlFor="task-tester">Assigned tester</label><select id="task-tester" name="testerId" value={formData.testerId ?? ""} onChange={handleInputChange} disabled={projectMembersLoading || saving || (Boolean(formData.projectId) && String(projectAssignableMembersFor) !== String(formData.projectId))}><option value="">Unassigned</option>{(formData.projectId ? selectedProjectAssignableMembers.filter(user=>user.tester) : testers).map(tester => <option key={tester.id} value={tester.id}>{tester.name}</option>)}</select>{projectMembersError && <small role="alert" className="text-xs text-red-300">{projectMembersError}</small>}</div></section> : editAction === "PLANNING" && canManage ? <section className="dfx-task-form-section"><h3>Project and sprint</h3><div className="dfx-task-field"><label htmlFor="task-project">Project</label><select id="task-project" name="projectId" value={formData.projectId} onChange={handleInputChange} required><option value="">Select a project</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select></div><div className="dfx-task-field"><label htmlFor="task-sprint">Sprint</label><select id="task-sprint" name="sprintId" value={formData.sprintId} onChange={handleInputChange} disabled={!formData.projectId}><option value="">No sprint</option>{sprintsForProject.map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name}</option>)}</select><small className="text-xs text-gray-500">Sprints are limited to the selected project; an incompatible sprint is cleared automatically.</small></div></section> : null}

                            <div className="flex flex-wrap justify-end gap-3 border-t border-gray-800 pt-4">
                                <button type="button" onClick={() => setIsModalOpen(false)} disabled={saving} className="df-button-secondary px-4 py-2.5">Cancel</button>
                                <button type="submit" disabled={saving || (isEditMode && editAction === "TESTER" && testersLoading)} className="df-button-primary px-5 py-2.5 disabled:opacity-50">{saving ? "Saving…" : isEditMode ? "Save change" : "Create task"}</button>
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
                                    Task #{selectedIssue.id}
                                </p>

                                <h2 className="text-2xl font-bold mt-1">
                                    {getTaskTitle(selectedIssue)}
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
                                        {dropdownLabel(priorityOptions,selectedIssue.priority,selectedIssue.priority)}
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
                                </div>                                <div className="bg-gray-800 rounded-lg p-4"><p className="text-xs text-gray-500 mb-2">Due date</p><p className="text-sm text-white">{selectedIssue.dueDate ? new Date(`${selectedIssue.dueDate}T00:00:00`).toLocaleDateString() : "No due date"}</p></div>

                                <div className="bg-gray-800 rounded-lg p-4"><p className="text-xs text-gray-500 mb-2">Reported by</p><div className="flex items-center gap-2"><Avatar name={selectedIssue.reportedBy?.name || selectedIssue.reporter?.name || "Unknown"} src={selectedIssue.reportedBy?.avatarUrl || selectedIssue.reporter?.avatarUrl} size={28} /><p className="text-sm text-white">{selectedIssue.reportedBy?.name || selectedIssue.reporter?.name || "—"}</p></div></div>
                                <div className="bg-gray-800 rounded-lg p-4"><p className="text-xs text-gray-500 mb-2">Tester</p><div className="flex items-center gap-2"><Avatar name={selectedIssue.tester?.name || selectedIssue.testerName || "Unassigned"} src={selectedIssue.tester?.avatarUrl} size={28} /><p className="text-sm text-white">{selectedIssue.tester?.name || selectedIssue.testerName || testers.find(tester => String(tester.id) === String(selectedIssue.tester?.id ?? selectedIssue.testerId))?.name || "Unassigned"}</p></div></div>
                                <div className="bg-gray-800 rounded-lg p-4"><p className="text-xs text-gray-500 mb-2">Created / updated</p><p className="text-sm text-white">{selectedIssue.createdAt ? new Date(selectedIssue.createdAt).toLocaleDateString() : "—"} / {selectedIssue.updatedAt ? new Date(selectedIssue.updatedAt).toLocaleDateString() : "—"}</p></div>

                            </div>

                            <div className="mb-6 flex flex-wrap gap-2">
                                {currentUser?.role === "DEVELOPER" && String(selectedIssue.assignedUser?.id) === String(currentUser.id) && ({OPEN:["IN_PROGRESS"],IN_PROGRESS:["CODE_REVIEW"],CODE_REVIEW:["READY_FOR_TESTING"]}[selectedIssue.status] || []).map(status => <button key={status} disabled={updatingStatusId === selectedIssue.id} onClick={() => updateStatus(selectedIssue,status)} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{updatingStatusId === selectedIssue.id ? "Saving…" : status === "IN_PROGRESS" ? "Start Work" : `Move to ${status.replaceAll("_"," ")}`}</button>)}
                                {canManage && (selectedIssue.status === "OPEN" ? ["IN_PROGRESS"] : selectedIssue.status === "IN_PROGRESS" ? ["CODE_REVIEW"] : selectedIssue.status === "CODE_REVIEW" ? ["READY_FOR_TESTING"] : []).map(status => <button key={status} disabled={updatingStatusId === selectedIssue.id} onClick={() => updateStatus(selectedIssue,status)} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{updatingStatusId === selectedIssue.id ? "Saving…" : `Move to ${status.replaceAll("_"," ")}`}</button>)}
                                {(currentUser?.role === "ADMIN" || (currentUser?.role === "TESTER" && String(selectedIssue.tester?.id) === String(currentUser.id))) && ["READY_FOR_TESTING", "TESTING"].includes(selectedIssue.status) && <button onClick={() => window.location.assign(`/testing/${selectedIssue.id}`)} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white">Open Testing Workspace</button>}
                            </div>

                            <section className="mb-8 rounded-xl border border-gray-700/80 bg-gray-900/70 p-5" aria-labelledby="task-approval-heading">
                                <div className="mb-4 flex flex-wrap items-start justify-between gap-3"><div><h3 id="task-approval-heading" className="text-lg font-semibold text-white">Testing & approvals</h3><p className="mt-1 text-sm text-gray-400">A passing test is followed by Team Lead approval, then Project Manager approval.</p></div><button type="button" onClick={() => { void loadApprovalHistory(selectedIssue.id); void loadMaintenance(selectedIssue.id); }} className="text-xs text-blue-300 underline">Refresh history</button></div>
                                {approvalLoading ? <p role="status" className="text-sm text-gray-400">Loading approval history…</p> : approvalError && <p role="alert" className="mb-3 text-sm text-red-300">{approvalError}</p>}
                                <p className="mb-3 text-sm text-gray-300">Current stage: <strong className="text-white">{statusLabel(selectedIssue.status)}</strong></p>
                                {selectedIssue.status === "TEAM_LEAD_APPROVAL" && canApproveTeamLead && <div className="mb-4 rounded-lg border border-gray-700 p-4"><p className="mb-2 text-sm font-medium text-white">Team Lead decision</p><label className="mb-3 block text-sm text-gray-300">Rejection remark (required to reject)<textarea value={approvalRemark} onChange={event => setApprovalRemark(event.target.value)} rows={2} className="mt-1 w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white" /></label><div className="flex gap-2"><button type="button" disabled={approvalSaving} onClick={() => void saveApprovalDecision("TEAM_LEAD", "APPROVED")} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{approvalSaving ? "Saving…" : "Approve to Manager"}</button><button type="button" disabled={approvalSaving || !approvalRemark.trim()} onClick={() => void saveApprovalDecision("TEAM_LEAD", "REJECTED")} className="rounded-lg border border-red-500/40 px-3 py-2 text-sm text-red-300 disabled:opacity-50">Reject to Developer</button></div></div>}
                                {selectedIssue.status === "PROJECT_MANAGER_APPROVAL" && canApproveProjectManager && <div className="mb-4 rounded-lg border border-gray-700 p-4"><p className="mb-2 text-sm font-medium text-white">Project Manager decision</p><label className="mb-3 block text-sm text-gray-300">Rejection remark (required to reject)<textarea value={approvalRemark} onChange={event => setApprovalRemark(event.target.value)} rows={2} className="mt-1 w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white" /></label><div className="flex gap-2"><button type="button" disabled={approvalSaving} onClick={() => void saveApprovalDecision("PROJECT_MANAGER", "APPROVED")} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{approvalSaving ? "Saving…" : "Approve and resolve"}</button><button type="button" disabled={approvalSaving || !approvalRemark.trim()} onClick={() => void saveApprovalDecision("PROJECT_MANAGER", "REJECTED")} className="rounded-lg border border-red-500/40 px-3 py-2 text-sm text-red-300 disabled:opacity-50">Reject to Developer</button></div></div>}
                                {approvalHistory.length > 0 && <ol className="space-y-2">{approvalHistory.map(entry => <li key={entry.id} className="rounded-lg bg-gray-800/70 p-3"><div className="flex flex-wrap justify-between gap-2"><strong className="text-sm text-white">{entry.stage?.replaceAll("_", " ")} · {entry.decision}</strong><time className="text-xs text-gray-500">{formatDate(entry.decidedAt) || "Time unavailable"}</time></div><p className="mt-1 text-xs text-gray-400">{entry.actor?.name || "Former/unavailable user"}{entry.remark ? ` · ${entry.remark}` : ""} · Cycle {entry.workflowCycle}</p></li>)}</ol>}
                                {!approvalLoading && !approvalError && approvalHistory.length === 0 && <p className="text-xs text-gray-500">No approval decisions have been recorded for this task.</p>}
                            </section>

                            <section className="mb-8 rounded-xl border border-gray-700/80 bg-gray-900/70 p-5" aria-labelledby="task-maintenance-heading">
                                <div className="mb-3"><h3 id="task-maintenance-heading" className="text-lg font-semibold text-white">Post-resolution maintenance</h3><p className="mt-1 text-sm text-gray-400">The original task remains resolved while this monitoring period is active.</p></div>
                                {maintenanceLoading ? <p role="status" className="text-sm text-gray-400">Loading maintenance details…</p> : maintenanceError && <p role="alert" className="mb-3 text-sm text-red-300">{maintenanceError}</p>}
                                {maintenance && <><p className="text-sm text-gray-300">Status: <strong className="text-white">{maintenance.status}</strong> · Started {formatDate(maintenance.startedAt) || "unknown"} · Expires {formatDate(maintenance.expiresAt) || "unknown"}</p>{maintenance.followUps?.length > 0 ? <ul className="mt-3 space-y-2">{maintenance.followUps.map(item => <li key={item.issueId} className="rounded-lg bg-gray-800/70 p-3"><button type="button" className="text-sm font-medium text-blue-300" onClick={() => void handleViewIssue({ id: item.issueId })}>#{item.issueId} · {item.title}</button><p className="mt-1 text-xs text-gray-400">{item.reason}</p><p className="mt-1 text-xs text-gray-500">Reported by {item.reporter?.name || "Unavailable"} · Assigned to {item.responsibleDeveloper?.name || "Unavailable"}</p></li>)}</ul> : <p className="mt-2 text-sm text-gray-500">No maintenance follow-ups recorded.</p>}</>}
                                {!maintenanceLoading && !maintenance && !maintenanceError && <p className="text-sm text-gray-500">No maintenance period is recorded. Existing resolved tasks are not assigned invented history.</p>}
                                {maintenance?.status === "ACTIVE" && hasProjectPermission("ISSUE_CREATE", selectedIssue.project?.id ?? selectedIssue.projectId) && <form className="mt-4 space-y-3 border-t border-gray-800 pt-4" onSubmit={createMaintenanceFollowUp}><h4 className="font-medium text-white">Report a follow-up</h4><select required aria-label="Responsible developer" value={maintenanceDeveloperId} onChange={event => setMaintenanceDeveloperId(event.target.value)}><option value="">Choose a project developer</option>{projectAssignableMembers.filter(person => person.developer).map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select><input aria-label="Follow-up task title" value={maintenanceTitle} onChange={event => setMaintenanceTitle(event.target.value)} placeholder="Optional follow-up title" maxLength={255} className="w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white" /><textarea required aria-label="Maintenance reason" value={maintenanceReason} onChange={event => setMaintenanceReason(event.target.value)} rows={3} placeholder="Describe the defect or regression" className="w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white" /><button type="submit" disabled={maintenanceSaving || !maintenanceDeveloperId || !maintenanceReason.trim()} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{maintenanceSaving ? "Creating…" : "Create linked follow-up task"}</button></form>}
                            </section>

                            <div className="mb-8">
                                <h3 className="text-lg font-semibold mb-3">
                                    Description
                                </h3>

                                <div className="bg-gray-800 rounded-lg p-4 text-gray-300 whitespace-pre-wrap">
                                    {selectedIssue.description || "No description provided."}
                                </div>
                            </div>

                            <section className="mb-8 rounded-xl border border-gray-700/80 bg-gray-900/80 p-5">
                                <div className="flex flex-wrap items-start justify-between gap-3">
                                    <div><h3 className="text-lg font-semibold text-white">Dependencies</h3><p className="mt-1 text-sm text-gray-400">Track blockers without changing the task lifecycle.</p></div>
                                    <span className={selectedIssue.blocked ? "rounded-full border border-amber-400/30 bg-amber-400/10 px-3 py-1 text-xs font-semibold text-amber-300" : "rounded-full border border-gray-700 bg-gray-800 px-3 py-1 text-xs font-medium text-gray-300"}>{selectedIssue.blocked ? "BLOCKED" : "No active blocker"}</span><button type="button" onClick={() => void refreshSelectedIssue()} disabled={dependencyLoading} className="rounded-lg border border-gray-700 px-3 py-1.5 text-xs text-gray-300 hover:border-blue-400/40 hover:text-blue-200 disabled:opacity-50">{dependencyLoading ? "Refreshing…" : "Refresh status"}</button>
                                </div>
                                {dependencyLoading && <p className="mt-4 text-sm text-gray-400" role="status">Refreshing dependency status…</p>}
                                {dependencyError && <div className="mt-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300" role="alert">{dependencyError}</div>}
                                {dependencyNotice && <p className="mt-4 text-sm text-emerald-300" role="status">{dependencyNotice}</p>}
                                <div className="mt-5 grid gap-4 lg:grid-cols-2">
                                    <div className="rounded-lg border border-gray-800 bg-gray-950/70 p-4">
                                        <h4 className="text-sm font-semibold text-gray-200">Blocked by</h4>
                                        {(selectedIssue.blockedBy || []).length === 0 ? <p className="mt-3 text-sm text-gray-500">No tasks are blocking this work.</p> : <ul className="mt-3 space-y-2">{selectedIssue.blockedBy.map(blocker => <li key={blocker.issueId} className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-gray-800 p-3">
                                            <div className="min-w-0"><button type="button" onClick={() => void handleViewIssue({ id: blocker.issueId })} className="text-left text-sm font-medium text-blue-300 hover:text-blue-200">#{blocker.issueId} · {blocker.title || "Untitled task"} →</button><p className="mt-1 text-xs text-gray-400">Current status: {blocker.status?.replaceAll("_", " ") || "Unknown"}</p><p className="mt-2 text-sm text-gray-300">{blocker.reason}</p></div>
                                            {canManage && <button type="button" disabled={dependencySaving} onClick={() => void handleRemoveDependency(blocker.issueId)} className="rounded-md border border-gray-700 px-2.5 py-1.5 text-xs text-gray-300 hover:border-red-400/40 hover:text-red-300 disabled:opacity-50">Remove</button>}
                                        </li>)}</ul>}
                                    </div>
                                    <div className="rounded-lg border border-gray-800 bg-gray-950/70 p-4">
                                        <h4 className="text-sm font-semibold text-gray-200">Tasks this blocks</h4>
                                        {(selectedIssue.blocks || []).length === 0 ? <p className="mt-3 text-sm text-gray-500">This task is not blocking other work.</p> : <ul className="mt-3 space-y-2">{selectedIssue.blocks.map(blockedIssue => <li key={blockedIssue.issueId} className="rounded-lg border border-gray-800 p-3"><button type="button" onClick={() => void handleViewIssue({ id: blockedIssue.issueId })} className="text-left text-sm font-medium text-blue-300 hover:text-blue-200">#{blockedIssue.issueId} · {blockedIssue.title || "Untitled task"} →</button><p className="mt-1 text-xs text-gray-400">Current status: {blockedIssue.status?.replaceAll("_", " ") || "Unknown"}</p><p className="mt-2 text-sm text-gray-300">{blockedIssue.reason}</p></li>)}</ul>}
                                    </div>
                                </div>
                                {canManage ? <form onSubmit={handleAddDependency} className="mt-4 grid gap-3 rounded-lg border border-blue-400/15 bg-blue-500/[0.04] p-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_auto]">
                                    <label className="text-xs text-gray-400">Blocking task<select required value={blockingIssueId} onChange={event => setBlockingIssueId(event.target.value)} className="mt-1.5 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-sm text-white"><option value="">Select a task…</option>{issues.filter(issue => issue.id !== selectedIssue.id && !(selectedIssue.blockedBy || []).some(link => String(link.issueId) === String(issue.id))).map(issue => <option key={issue.id} value={issue.id}>#{issue.id} · {issue.title || "Untitled task"} ({statusLabel(issue.status) || "Unknown"})</option>)}</select></label>
                                    <label className="text-xs text-gray-400">Why is this blocked?<input required maxLength={500} value={blockingReason} onChange={event => setBlockingReason(event.target.value)} placeholder="Describe the dependency" className="mt-1.5 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-sm text-white placeholder:text-gray-600" /></label>
                                    <button type="submit" disabled={dependencySaving || dependencyLoading || !blockingIssueId || !blockingReason.trim()} className="self-end rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-50">{dependencySaving ? "Saving…" : "Add blocker"}</button>
                                </form> : <p className="mt-4 text-xs text-gray-500">Only administrators and project managers can change task dependencies.</p>}
                            </section>
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
                                            Task changes and workflow events in chronological order
                                        </p>
                                        <button type="button" onClick={() => fetchIssueActivity(selectedIssue.id)} disabled={activityLoading} className="mt-2 text-xs text-blue-300 underline hover:text-blue-200 disabled:opacity-50">{activityLoading ? "Refreshing…" : "Refresh"}</button>
                                    </div>
                                </div>

                                {activityLoading ? (
                                    <div className="rounded-lg bg-gray-800 px-4 py-6 text-center text-sm text-gray-400" role="status">
                                        Loading task activity…
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
                                                            {formatDate(log.occurredAt) || "Time not provided"}
                                                        </span>
                                                    </div>

                                                    <p className="text-gray-400 text-sm mt-2">{log.details}</p>
                                                    {log.repository && <p className="mt-2 text-xs text-gray-500">{log.repository}</p>}
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
