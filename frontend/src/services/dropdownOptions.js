import { apiUrl } from "./apiConfig";

/** Loads centrally managed labels while preserving the application's stable option codes. */
export async function loadDropdownOptions(category) {
    const token = localStorage.getItem("token");
    const response = await fetch(apiUrl(`/api/config/dropdowns/${encodeURIComponent(category)}`), {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    if (!response.ok) throw new Error(`Could not load ${category.toLowerCase().replaceAll("_", " ")} options (HTTP ${response.status}).`);
    const data = await response.json();
    return Array.isArray(data) ? data : [];
}

export const dropdownLabel = (options, code, fallback = code) =>
    options?.find(option => option.code === code)?.displayLabel || fallback || "";
