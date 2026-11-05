import assert from "node:assert/strict";
import test from "node:test";
import { isDateOnlyOverdue, utcDateKey } from "./dateOnly.js";

test("UTC day boundary matches the backend calendar date", () => {
    const instant = new Date("2026-10-27T19:15:00-07:00");
    assert.equal(utcDateKey(instant), "2026-10-28");
    assert.equal(isDateOnlyOverdue("2026-10-27", "IN_PROGRESS", utcDateKey(instant)), true);
    assert.equal(isDateOnlyOverdue("2026-10-28", "IN_PROGRESS", utcDateKey(instant)), false);
});

test("due-today and resolved tasks are not overdue", () => {
    assert.equal(isDateOnlyOverdue("2026-10-28", "OPEN", "2026-10-28"), false);
    assert.equal(isDateOnlyOverdue("2026-10-27", "RESOLVED", "2026-10-28"), false);
    assert.equal(isDateOnlyOverdue(null, "OPEN", "2026-10-28"), false);
});