import { useEffect, useMemo, useRef, useState } from "react";

const displayName = person => person?.name?.trim() || "Unnamed member";

function defaultMeta(person) {
    const values = [person?.responsibility, person?.role];
    if (person?.developer) values.push("Developer eligible");
    if (person?.tester) values.push("Tester eligible");
    return [...new Set(values.filter(Boolean).map(value => String(value).replaceAll("_", " "))) ].join(" · ") || "Registered member";
}

export default function SearchableMemberSelector({
    id,
    label,
    people = [],
    selected = [],
    onChange,
    excluded = [],
    minCount = 1,
    getMeta = defaultMeta,
    disabled = false,
    emptyMessage = "No eligible members are available."
}) {
    const [open, setOpen] = useState(false);
    const [search, setSearch] = useState("");
    const rootRef = useRef(null);
    const searchRef = useRef(null);
    const selectedIds = useMemo(() => new Set(selected.map(String)), [selected]);
    const excludedIds = useMemo(() => new Set(excluded.map(String)), [excluded]);
    const selectedPeople = useMemo(() => selected.map(value => people.find(person => String(person.id) === String(value))).filter(Boolean), [people, selected]);
    const available = useMemo(() => people.filter(person => !excludedIds.has(String(person.id)) && !selectedIds.has(String(person.id)) &&
        `${displayName(person)} ${getMeta(person)}`.toLowerCase().includes(search.trim().toLowerCase())), [people, excludedIds, selectedIds, search, getMeta]);

    useEffect(() => {
        if (!open) return undefined;
        const onPointerDown = event => { if (!rootRef.current?.contains(event.target)) setOpen(false); };
        const onKeyDown = event => { if (event.key === "Escape") setOpen(false); };
        document.addEventListener("mousedown", onPointerDown);
        document.addEventListener("keydown", onKeyDown);
        searchRef.current?.focus();
        return () => {
            document.removeEventListener("mousedown", onPointerDown);
            document.removeEventListener("keydown", onKeyDown);
        };
    }, [open]);

    const update = (person, checked) => {
        const idValue = String(person.id);
        const next = checked ? [...selected, idValue] : selected.filter(value => String(value) !== idValue);
        onChange(next);
    };

    return <section ref={rootRef} className="w-full min-w-0 rounded-xl border border-gray-700/80 bg-gray-900/60 p-4 sm:p-5" aria-labelledby={`${id}-heading`}>
        <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
                <h3 id={`${id}-heading`} className="text-sm font-semibold text-gray-100">{label}</h3>
                <p className="mt-1 text-xs text-gray-400">At least {minCount} required. Select one or more registered members.</p>
            </div>
            <span className={`rounded-full border px-2.5 py-1 text-xs font-medium ${selected.length >= minCount ? "border-emerald-500/25 bg-emerald-500/10 text-emerald-200" : "border-amber-500/25 bg-amber-500/10 text-amber-200"}`} aria-live="polite">
                {selected.length} selected{selected.length < minCount ? ` · ${minCount - selected.length} required` : ""}
            </span>
        </div>

        <div className="mt-4">
            <p className="mb-2 text-xs font-medium text-gray-400">Selected members</p>
            {selectedPeople.length ? <ul className="flex flex-wrap gap-2" aria-label={`${label} selected members`}>
                {selectedPeople.map(person => <li key={person.id} className="inline-flex max-w-full items-center gap-2 rounded-lg border border-blue-400/20 bg-blue-500/10 py-1.5 pl-3 pr-1.5">
                    <span className="min-w-0 truncate text-sm text-blue-100">{displayName(person)}</span>
                    <button type="button" disabled={disabled} aria-label={`Remove ${displayName(person)} from ${label}`} onClick={() => update(person, false)} className="grid h-7 w-7 shrink-0 place-items-center rounded-md text-gray-300 hover:bg-red-500/15 hover:text-red-200 disabled:opacity-50">×</button>
                </li>)}
            </ul> : <p className="rounded-lg border border-dashed border-gray-700 px-3 py-3 text-sm text-gray-500">No members selected yet.</p>}
        </div>

        <div className="mt-4">
            <button type="button" disabled={disabled} aria-expanded={open} aria-controls={`${id}-options`} onClick={() => { setOpen(value => !value); setSearch(""); }} className="flex min-h-11 w-full items-center justify-between gap-3 rounded-lg border border-gray-700 bg-gray-950 px-3.5 py-2.5 text-left text-sm text-gray-200 transition hover:border-blue-400/50 hover:bg-gray-950/80 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-400 disabled:opacity-50">
                <span>{open ? "Close member list" : `Search and add ${label.toLowerCase()}`}</span><span aria-hidden="true" className="text-gray-500">{open ? "⌃" : "⌄"}</span>
            </button>
            {open && <div id={`${id}-options`} className="mt-2 overflow-hidden rounded-lg border border-gray-700 bg-gray-950 shadow-xl">
                <label className="block border-b border-gray-800 p-3">
                    <span className="sr-only">Search {label}</span>
                    <input ref={searchRef} type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Search by name or role…" className="min-h-11 w-full rounded-lg border border-gray-700 bg-gray-900 px-3.5 text-sm text-gray-100 placeholder:text-gray-500 focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20" />
                </label>
                <ul className="max-h-72 overflow-y-auto p-2" aria-label={`Available ${label}`}>
                    {available.map(person => {
                        const checkboxId = `${id}-person-${person.id}`;
                        return <li key={person.id}>
                            <label htmlFor={checkboxId} className="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-3 hover:bg-gray-800/80 focus-within:bg-gray-800/80">
                                <input id={checkboxId} type="checkbox" checked={selectedIds.has(String(person.id))} onChange={event => update(person, event.target.checked)} className="h-4 w-4 shrink-0 accent-blue-500" />
                                <span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium text-gray-100">{displayName(person)}</span><span className="mt-0.5 block truncate text-xs text-gray-400">{getMeta(person)}</span></span>
                            </label>
                        </li>;
                    })}
                    {!available.length && <li className="px-3 py-6 text-center text-sm text-gray-500">{search ? "No members match this search." : emptyMessage}</li>}
                </ul>
                <div className="flex justify-end border-t border-gray-800 px-3 py-2"><button type="button" onClick={() => setOpen(false)} className="rounded-md px-3 py-2 text-xs font-medium text-blue-200 hover:bg-blue-500/10">Done</button></div>
            </div>}
        </div>
    </section>;
}
