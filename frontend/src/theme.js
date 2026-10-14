export const THEME_KEY = "devflow_theme";

export function getTheme() {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored === "light" || stored === "dark") return stored;
    localStorage.setItem(THEME_KEY, "dark");
    return "dark";
}

export function applyTheme(theme) {
    const selected = theme === "light" ? "light" : "dark";
    localStorage.setItem(THEME_KEY, selected);
    document.documentElement.dataset.theme = selected;
    document.documentElement.style.colorScheme = selected;
    return selected;
}
