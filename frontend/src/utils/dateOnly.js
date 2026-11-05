export function utcDateKey(date) {
    return date.toISOString().slice(0, 10);
}

export function isDateOnlyOverdue(dueDate, status, today) {
    return Boolean(dueDate && status !== "RESOLVED" && dueDate < today);
}