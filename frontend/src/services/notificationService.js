const API_URL = "http://localhost:8080/api/notifications";

const getHeaders = () => ({
    Authorization: `Bearer ${localStorage.getItem("token")}`,
    "Content-Type": "application/json"
});

const getErrorMessage = async (response, fallback) => {
    const body = await response.text();
    if (!body) return fallback;
    try {
        const data = JSON.parse(body);
        return data.message || data.error || data.detail || fallback;
    } catch {
        return body;
    }
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
