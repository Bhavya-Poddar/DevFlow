const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();

export const API_BASE_URL = (configuredApiBaseUrl || "http://localhost:8080").replace(/\/+$/, "");

export const apiUrl = path => `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;

// SockJS uses HTTP(S) for its negotiation endpoint and upgrades to WS(S) when available.
export const webSocketUrl = () => apiUrl("/ws");
