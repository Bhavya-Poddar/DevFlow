import { responseErrorMessage } from "../utils/responseError";
import { apiUrl } from "./apiConfig";

async function request(path, method = "GET", payload) {
    const token = localStorage.getItem("token");
    const response = await fetch(apiUrl(path), {
        method,
        headers: {
            ...(payload ? { "Content-Type": "application/json" } : {}),
            ...(token ? { Authorization: "Bearer " + token } : {})
        },
        ...(payload ? { body: JSON.stringify(payload) } : {})
    });
    if (!response.ok) throw new Error(await responseErrorMessage(response, "Automation request failed."));
    if (response.status === 204) return null;
    const body = await response.text();
    return body ? JSON.parse(body) : null;
}

export const listAutomationRules = () => request("/api/automations/rules");
export const listAutomationExecutions = () => request("/api/automations/executions?limit=50");
export const createAutomationRule = payload => request("/api/automations/rules", "POST", payload);
export const updateAutomationRule = (id, payload) => request("/api/automations/rules/" + encodeURIComponent(id), "PUT", payload);
export const setAutomationRuleEnabled = (id, enabled) => request("/api/automations/rules/" + encodeURIComponent(id) + "/enabled", "PATCH", { enabled });
export const deleteAutomationRule = id => request("/api/automations/rules/" + encodeURIComponent(id), "DELETE");