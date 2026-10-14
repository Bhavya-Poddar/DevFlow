import { responseErrorMessage } from "../utils/responseError";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
export const apiUrl = path => `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;

async function issueRequest(path, token, options = {}, fallback) {
    const response = await fetch(apiUrl(`/api/issues${path}`), {
        ...options,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(options.headers || {})
        }
    });

    if (!response.ok) {
        throw new Error(await responseErrorMessage(response, fallback));
    }

    if (response.status === 204) return null;
    const body = await response.text();
    if (!body) return null;

    const contentType = response.headers.get("content-type") || "";
    return contentType.includes("application/json") ? JSON.parse(body) : body;
}

const jsonOptions = (method, payload) => ({
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
});

export const listIssues = token => issueRequest("", token, {}, "Failed to fetch issues.");
export const getIssue = (token, id) => issueRequest(`/${encodeURIComponent(id)}`, token, {}, "Failed to fetch issue.");
export const getIssueActivity = (token, id) => issueRequest(`/${encodeURIComponent(id)}/activity`, token, {}, "Failed to fetch issue activity.");
export const createIssue = (token, payload) => issueRequest("", token, jsonOptions("POST", payload), "Failed to create issue.");
export const updateIssue = (token, id, payload) => issueRequest(`/${encodeURIComponent(id)}`, token, jsonOptions("PUT", payload), "Failed to update issue.");
export const deleteIssue = (token, id) => issueRequest(`/${encodeURIComponent(id)}`, token, { method: "DELETE" }, "Failed to delete issue.");
export const assignIssueTester = (token, id, testerId) => issueRequest(`/${encodeURIComponent(id)}/tester`, token, jsonOptions("PUT", { testerId }), "Failed to assign tester.");
