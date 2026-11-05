import { apiUrl } from "./apiConfig";

const API_URL = apiUrl("/api/notifications");

const getHeaders = () => ({
    Authorization: `Bearer ${localStorage.getItem("token")}`,
    "Content-Type": "application/json"
});

const getErrorMessage = async (response, fallback) => {
    let body = "";
    try {
        body = (await response.text()).trim();
    } catch {
        // Keep the HTTP status diagnostic if the response body cannot be read.
    }

    let message = "";
    try {
        const data = body ? JSON.parse(body) : null;
        if (data && typeof data === "object") {
            for (const key of ["message", "detail", "error"]) {
                if (typeof data[key] === "string" && data[key].trim()) {
                    message = data[key].trim();
                    break;
                }
            }
        }
    } catch {
        // Ignore unstructured proxy/server bodies instead of showing HTML or internals.
    }

    const status = `HTTP ${response.status}${response.statusText ? ` ${response.statusText}` : ""}`;
    return `${message || fallback} (${status})`;
};

export const normalizeNotifications = (data) => {
    const notifications = Array.isArray(data)
        ? data
        : Array.isArray(data?.content)
            ? data.content
            : [];
    return notifications
        .filter(notification => notification && typeof notification === "object")
        .map(notification => ({
            ...notification,
            read: notification.read ?? notification.isRead ?? Boolean(notification.readAt)
        }));
};

export const getNotifications = async () => {
    const response = await fetch(API_URL, { headers: getHeaders() });
    if (!response.ok) throw new Error(await getErrorMessage(response, "Failed to fetch notifications."));
    return response.json();
};

export const getNotificationPage = async ({ page = 0, size = 20, read, search } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (read != null) params.set("read", String(read));
    if (search?.trim()) params.set("search", search.trim());
    const response = await fetch(`${API_URL}/page?${params}`, { headers: getHeaders() });
    if (!response.ok) throw new Error(await getErrorMessage(response, "Failed to fetch notification history."));
    return response.json();
};

export const getUnreadNotificationCount = async () => {
    const response = await fetch(`${API_URL}/unread/count`, { headers: getHeaders() });
    if (!response.ok) throw new Error(await getErrorMessage(response, "Failed to fetch unread notification count."));
    return response.json();
};

export const getUnreadNotifications = async () => {
    const response = await fetch(`${API_URL}/unread`, { headers: getHeaders() });
    if (!response.ok) throw new Error(await getErrorMessage(response, "Failed to fetch unread notifications."));
    return response.json();
};

export const markNotificationAsRead = async (id) => {
    const response = await fetch(`${API_URL}/${id}/read`, {
        method: "PUT",
        headers: getHeaders()
    });
    if (!response.ok) throw new Error(await getErrorMessage(response, "Failed to mark notification as read."));
};

export const markAllNotificationsAsRead = async () => {
    const response = await fetch(`${API_URL}/read-all`, {
        method: "PUT",
        headers: getHeaders()
    });
    if (!response.ok) throw new Error(await getErrorMessage(response, "Failed to mark notifications as read."));
};
