import { useCallback, useEffect, useMemo, useState } from "react";
import { createAutomationRule, deleteAutomationRule, listAutomationExecutions, listAutomationRules, setAutomationRuleEnabled, updateAutomationRule } from "../services/automationService";
import { loadDropdownOptions, dropdownLabel } from "../services/dropdownOptions";

const DEFAULT_TRIGGERS = [{code:"TASK_STATUS_CHANGED",displayLabel:"Task status changed"},{code:"TESTING_PASSED",displayLabel:"Testing passed"},{code:"TESTING_FAILED",displayLabel:"Testing failed"},{code:"BLOCKING_TASK_RESOLVED",displayLabel:"Blocking task resolved"}];
const DEFAULT_ACTIONS = [{code:"NOTIFY_ASSIGNED_DEVELOPER",displayLabel:"Notify assigned developer"},{code:"NOTIFY_ASSIGNED_TESTER",displayLabel:"Notify assigned tester"},{code:"NOTIFY_UNBLOCKED_OWNERS",displayLabel:"Notify owners of unblocked tasks"}];
const DEFAULT_STATUSES = ["OPEN", "IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING", "TESTING", "RESOLVED"].map(code=>({code,displayLabel:code.replaceAll("_"," ")}));
const blank = () => ({ name: "", description: "", enabled: true, triggerEvent: "TASK_STATUS_CHANGED", conditionStatus: "READY_FOR_TESTING", actionType: "NOTIFY_ASSIGNED_TESTER" });

export default function Automations() {
    const [rules, setRules] = useState([]);
    const [runs, setRuns] = useState([]);
    const [query, setQuery] = useState("");
    const [rulesLoading, setRulesLoading] = useState(true);
    const [runsLoading, setRunsLoading] = useState(true);
    const [rulesError, setRulesError] = useState("");
    const [runsError, setRunsError] = useState("");
    const [draft, setDraft] = useState(null);
    const [editing, setEditing] = useState(null);
    const [saving, setSaving] = useState(false);
    const [formError, setFormError] = useState("");
    const [triggers, setTriggers] = useState(DEFAULT_TRIGGERS);
    const [actions, setActions] = useState(DEFAULT_ACTIONS);
    const [statuses, setStatuses] = useState(DEFAULT_STATUSES);

    useEffect(() => {
        let active = true;
        Promise.all([loadDropdownOptions("AUTOMATION_TRIGGER"), loadDropdownOptions("AUTOMATION_ACTION"), loadDropdownOptions("ISSUE_STATUS")])
            .then(([triggerData, actionData, statusData]) => { if (active) { if (triggerData.length) setTriggers(triggerData); if (actionData.length) setActions(actionData); if (statusData.length) setStatuses(statusData); } })
            .catch(() => {});
        return () => { active = false; };
    }, []);
    const actionLabel = value => dropdownLabel(actions, value, value || "—");
    const triggerLabel = value => dropdownLabel(triggers, value, value || "—");

    const loadRules = useCallback(async () => {
        setRulesLoading(true);
        setRulesError("");
        try {
            const result = await listAutomationRules();
            if (!Array.isArray(result)) throw new Error("The rules response was not a list.");
            setRules(result);
        } catch (problem) {
            setRulesError(problem.message || "Could not load automation rules.");
        } finally { setRulesLoading(false); }
    }, []);
    const loadRuns = useCallback(async () => {
        setRunsLoading(true);
        setRunsError("");
        try {
            const result = await listAutomationExecutions();
            if (!Array.isArray(result)) throw new Error("The execution response was not a list.");
            setRuns(result);
        } catch (problem) {
            setRunsError(problem.message || "Could not load automation history.");
        } finally { setRunsLoading(false); }
    }, []);
    const load = useCallback(() => Promise.all([loadRules(), loadRuns()]), [loadRules, loadRuns]);
    useEffect(() => { const timer = window.setTimeout(() => load(), 0); return () => window.clearTimeout(timer); }, [load]);
    const visibleRules = useMemo(() => {
        const value = query.trim().toLowerCase();
        return rules.filter(rule => !value || [rule.name, rule.description, rule.triggerEvent, rule.actionType].some(part => part?.toLowerCase().includes(value)));
    }, [rules, query]);

    const openCreate = () => { setEditing(null); setDraft(blank()); setFormError(""); };
    const openEdit = rule => {
        setEditing(rule);
        setDraft({ name: rule.name, description: rule.description || "", enabled: rule.enabled, triggerEvent: rule.triggerEvent, conditionStatus: rule.conditionStatus || "", actionType: rule.actionType });
        setFormError("");
    };
    const save = async event => {
        event.preventDefault();
        setSaving(true);
        setFormError("");
        try {
            const payload = { ...draft, description: draft.description.trim() || null, conditionStatus: draft.conditionStatus || null };
            if (editing) await updateAutomationRule(editing.id, payload);
            else await createAutomationRule(payload);
            setDraft(null);
            setEditing(null);
            await load();
        } catch (problem) { setFormError(problem.message || "Could not save rule."); }
        finally { setSaving(false); }
    };
    const toggle = async rule => {
        try {
            const updated = await setAutomationRuleEnabled(rule.id, !rule.enabled);
            setRules(current => current.map(item => item.id === updated.id ? updated : item));
        } catch (problem) { setRulesError(problem.message || "Could not update rule."); }
    };
    const remove = async rule => {
        if (!window.confirm("Delete automation rule: " + rule.name + "?")) return;
        try { await deleteAutomationRule(rule.id); await load(); }
        catch (problem) { setRulesError(problem.message || "Could not delete rule."); }
    };

    return <div className="space-y-6">
        <section className="flex flex-col gap-4 rounded-2xl border border-gray-800 bg-gradient-to-br from-gray-900 to-gray-950 p-5 sm:flex-row sm:items-center sm:justify-between sm:p-7">
            <div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-blue-300">Workflow automation</p><h1 className="mt-2 text-2xl font-semibold tracking-tight text-white">Automations</h1><p className="mt-1 text-sm text-gray-400">Run notification rules after task and testing changes commit. Project-manager routing is unavailable because projects do not currently have manager assignments.</p></div>
            <button type="button" onClick={openCreate} className="rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-500">Create rule</button>
        </section>

        <section className="overflow-hidden rounded-2xl border border-gray-800 bg-gray-900/70">
            <div className="flex flex-col gap-3 border-b border-gray-800 p-4 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="font-semibold text-white">Rules</h2><p className="mt-1 text-xs text-gray-500">{rules.length} configured</p></div><input aria-label="Search automation rules" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search rules..." className="w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2 text-sm text-gray-100 outline-none placeholder:text-gray-500 focus:border-blue-500 sm:max-w-xs" /></div>
            {rulesLoading ? <p className="p-8 text-center text-sm text-gray-400">Loading rules…</p> : <>{rulesError && <div role="alert" className="flex justify-between gap-3 border-b border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-200"><span>{rulesError}</span><button onClick={loadRules} type="button" className="underline">Retry rules</button></div>}{visibleRules.length === 0 ? rulesError ? null : <p className="p-8 text-center text-sm text-gray-400">{rules.length ? "No rules match your search." : "No rules are configured yet."}</p> :
                <div className="overflow-x-auto"><table className="w-full min-w-[820px] text-left text-sm"><thead className="bg-gray-950/70 text-xs uppercase tracking-wide text-gray-500"><tr><th className="px-4 py-3">Rule</th><th className="px-4 py-3">Trigger / condition</th><th className="px-4 py-3">Action</th><th className="px-4 py-3">State</th><th className="px-4 py-3 text-right">Manage</th></tr></thead>
                    <tbody className="divide-y divide-gray-800">{visibleRules.map(rule => <tr key={rule.id} className="hover:bg-gray-800/30"><td className="max-w-sm px-4 py-4"><p className="font-medium text-gray-100">{rule.name}</p><p className="mt-1 line-clamp-2 text-xs text-gray-500">{rule.description || "No description"}</p></td><td className="px-4 py-4 text-gray-300">{triggerLabel(rule.triggerEvent)}{rule.conditionStatus && <p className="mt-1 text-xs text-gray-500">Status is {dropdownLabel(statuses,rule.conditionStatus,rule.conditionStatus)}</p>}</td><td className="px-4 py-4 text-gray-300">{actionLabel(rule.actionType)}</td><td className="px-4 py-4"><span className={"rounded-full px-2.5 py-1 text-xs " + (rule.enabled ? "bg-emerald-500/10 text-emerald-300" : "bg-gray-800 text-gray-400")}>{rule.enabled ? "Enabled" : "Disabled"}</span></td><td className="px-4 py-4"><div className="flex justify-end gap-2"><button type="button" onClick={() => toggle(rule)} className="rounded-md border border-gray-700 px-2.5 py-1.5 text-xs text-gray-300">{rule.enabled ? "Disable" : "Enable"}</button><button type="button" onClick={() => openEdit(rule)} className="rounded-md border border-gray-700 px-2.5 py-1.5 text-xs text-gray-300">Edit</button><button type="button" onClick={() => remove(rule)} className="rounded-md border border-red-500/30 px-2.5 py-1.5 text-xs text-red-300">Delete</button></div></td></tr>)}</tbody>
                </table></div>}</>}
        </section>
        <section className="overflow-hidden rounded-2xl border border-gray-800 bg-gray-900/70"><div className="border-b border-gray-800 p-4"><h2 className="font-semibold text-white">Recent runs</h2><p className="mt-1 text-xs text-gray-500">Latest processed rule outcomes</p></div>
            {runsLoading ? <p className="p-6 text-sm text-gray-400">Loading recent runs…</p> : <>{runsError && <div role="alert" className="flex justify-between gap-3 border-b border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-200"><span>{runsError}</span><button onClick={loadRuns} type="button" className="underline">Retry history</button></div>}{runs.length === 0 ? runsError ? null : <p className="p-6 text-sm text-gray-400">No automation executions yet. Matching events appear here after processing.</p> :
                <div className="divide-y divide-gray-800">{runs.map(run => <div key={run.id} className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0"><p className="truncate text-sm font-medium text-gray-100">{run.ruleName}</p><p className="mt-1 text-xs text-gray-500">{triggerLabel(run.triggerEvent)} · {actionLabel(run.actionType)}{run.targetIssueId ? " · Task #" + run.targetIssueId : ""}</p><p className="mt-1 text-xs text-gray-400">{run.summary}</p></div><div className="flex items-center gap-3"><span className={"rounded-full px-2.5 py-1 text-xs " + (run.outcome === "SUCCESS" ? "bg-emerald-500/10 text-emerald-300" : run.outcome === "FAILED" ? "bg-red-500/10 text-red-300" : "bg-amber-500/10 text-amber-300")}>{run.outcome}</span><time className="text-xs text-gray-500">{run.createdAt ? new Date(run.createdAt).toLocaleString() : "—"}</time></div></div>)}</div>}</>}
        </section>
        {draft && <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/70 p-4" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) { setDraft(null); setEditing(null); } }}><section role="dialog" aria-modal="true" aria-labelledby="automation-form-title" className="my-auto w-full max-w-xl rounded-2xl border border-gray-700 bg-gray-900 p-5 shadow-2xl sm:p-6"><div className="flex justify-between gap-4"><div><h2 id="automation-form-title" className="text-lg font-semibold text-white">{editing ? "Edit rule" : "Create rule"}</h2><p className="mt-1 text-sm text-gray-400">Choose a supported trigger, optional status condition, and notification action.</p></div><button type="button" aria-label="Close" onClick={() => { setDraft(null); setEditing(null); }} className="text-gray-400">×</button></div>
            <form onSubmit={save} className="mt-5 space-y-4"><label className="block text-sm text-gray-300">Name *<input required maxLength={120} value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })} className="mt-1.5 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-gray-100" /></label><label className="block text-sm text-gray-300">Description<textarea rows={2} maxLength={1000} value={draft.description} onChange={event => setDraft({ ...draft, description: event.target.value })} className="mt-1.5 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-gray-100" /></label>
                <div className="grid gap-4 sm:grid-cols-2"><label className="block text-sm text-gray-300">Trigger<select value={draft.triggerEvent} onChange={event => setDraft({ ...draft, triggerEvent: event.target.value, actionType: event.target.value === "BLOCKING_TASK_RESOLVED" && draft.actionType === "NOTIFY_UNBLOCKED_OWNERS" ? draft.actionType : draft.actionType === "NOTIFY_UNBLOCKED_OWNERS" ? "NOTIFY_ASSIGNED_DEVELOPER" : draft.actionType })} className="mt-1.5 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-gray-100">{triggers.map(item => <option key={item.code} value={item.code}>{item.displayLabel}</option>)}</select></label><label className="block text-sm text-gray-300">Action<select value={draft.actionType} onChange={event => setDraft({ ...draft, actionType: event.target.value })} className="mt-1.5 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-gray-100">{actions.filter(item => item.code !== "NOTIFY_UNBLOCKED_OWNERS" || draft.triggerEvent === "BLOCKING_TASK_RESOLVED").map(item => <option key={item.code} value={item.code}>{item.displayLabel}</option>)}</select></label></div>
                <label className="block text-sm text-gray-300">Only when resulting status is <span className="text-gray-500">(optional)</span><select value={draft.conditionStatus} onChange={event => setDraft({ ...draft, conditionStatus: event.target.value })} className="mt-1.5 w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-gray-100"><option value="">Any status</option>{statuses.map(status => <option key={status.code} value={status.code}>{status.displayLabel}</option>)}</select></label><label className="flex items-center gap-2 text-sm text-gray-300"><input type="checkbox" checked={draft.enabled} onChange={event => setDraft({ ...draft, enabled: event.target.checked })} className="accent-blue-500" />Enable rule</label>
                {formError && <p role="alert" className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-200">{formError}</p>}<div className="flex justify-end gap-2 border-t border-gray-800 pt-4"><button type="button" onClick={() => { setDraft(null); setEditing(null); }} className="rounded-lg border border-gray-700 px-4 py-2 text-sm text-gray-300">Cancel</button><button type="submit" disabled={saving} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60">{saving ? "Saving…" : "Save rule"}</button></div>
            </form></section></div>}
    </div>;
}
