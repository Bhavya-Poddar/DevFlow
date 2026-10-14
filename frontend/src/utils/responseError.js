export async function responseErrorMessage(response, fallback) {
    if (!response || !Number.isFinite(response.status) || typeof response.text !== "function") {
        return typeof response?.message === "string" && response.message.trim()
            ? response.message.trim()
            : fallback;
    }
    let body = "";
    try {
        body = (await response.text()).trim();
    } catch {
        // Keep the status-based fallback if the response body cannot be read.
    }

    let message = "";
    if (body) {
        try {
            const data = JSON.parse(body);
            if (typeof data === "string") {
                message = data.trim();
            } else if (data && typeof data === "object") {
                for (const key of ["detail", "message", "error", "title"]) {
                    if (typeof data[key] === "string" && data[key].trim()) {
                        message = data[key].trim();
                        break;
                    }
                }
            }
        } catch {
            // Plain-text response bodies are useful and should be shown as-is.
        }

        if (!message) message = body.slice(0, 600);
    }

    const status = `HTTP ${response.status}${response.statusText ? ` ${response.statusText}` : ""}`;
    return message ? `${message} (${status})` : `${fallback} (${status})`;
}
