export function notificationTargetPath(value) {
    if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")
        || value.includes("\\") || value.includes("\r") || value.includes("\n")) return null;
    try {
        const target = new URL(value, window.location.origin);
        if (target.origin !== window.location.origin) return null;
        return target.pathname + target.search + target.hash;
    } catch {
        return null;
    }
}