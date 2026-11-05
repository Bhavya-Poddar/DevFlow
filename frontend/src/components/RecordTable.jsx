import { createPortal } from "react-dom";
import { useEffect, useId, useMemo, useRef, useState } from "react";

/** Shared searchable, sortable, paginated table for record-management screens. */
export default function RecordTable({
    rows = [], columns = [], getRowId = row => row.id,
    getSearchText = row => Object.values(row || {}).filter(value => ["string", "number"].includes(typeof value)).join(" "),
    actionsForRow = () => [], emptyLabel = "No records found.", searchLabel = "Search records",
    searchPlaceholder = "Search records…", pageSize = 10, onRowClick, showSearch = true
}) {
    const [search, setSearch] = useState("");
    const [sort, setSort] = useState({ key: "", direction: "asc" });
    const [page, setPage] = useState(0);
    const [menu, setMenu] = useState(null);
    const menuRef = useRef(null);
    const menuTriggerRef = useRef(null);
    const searchId = useId();
    const filtered = useMemo(() => {
        const query = search.trim().toLocaleLowerCase();
        const result = rows.filter(row => !query || String(getSearchText(row) || "").toLocaleLowerCase().includes(query));
        if (!sort.key) return result;
        const column = columns.find(item => item.key === sort.key);
        if (!column?.sortValue) return result;
        return [...result].sort((a, b) => {
            const left = column.sortValue(a);
            const right = column.sortValue(b);
            const comparison = typeof left === "number" && typeof right === "number"
                ? left - right : String(left ?? "").localeCompare(String(right ?? ""), undefined, { numeric: true, sensitivity: "base" });
            return sort.direction === "asc" ? comparison : -comparison;
        });
    }, [rows, columns, getSearchText, search, sort]);
    const pageCount = Math.ceil(filtered.length / pageSize);
    const safePage = Math.min(page, Math.max(pageCount - 1, 0));
    const visibleRows = filtered.slice(safePage * pageSize, (safePage + 1) * pageSize);
    useEffect(() => {
        if (!menu) return undefined;
        const close = event => {
            if (event.key === "Escape") {
                setMenu(null);
                requestAnimationFrame(() => menuTriggerRef.current?.focus());
            }
            if (event.type === "mousedown" && !menuRef.current?.contains(event.target)) setMenu(null);
            if (event.type === "keydown" && ["ArrowDown", "ArrowUp"].includes(event.key)) {
                event.preventDefault();
                const items = [...(menuRef.current?.querySelectorAll('[role="menuitem"]') || [])];
                const current = items.indexOf(document.activeElement);
                const step = event.key === "ArrowDown" ? 1 : -1;
                items[(current + step + items.length) % items.length]?.focus();
            }
            if (event.type === "keydown" && ["Home", "End"].includes(event.key)) {
                event.preventDefault();
                const items = menuRef.current?.querySelectorAll('[role="menuitem"]');
                (event.key === "Home" ? items?.[0] : items?.[items.length - 1])?.focus();
            }
        };
        document.addEventListener("keydown", close);
        document.addEventListener("mousedown", close);
        return () => { document.removeEventListener("keydown", close); document.removeEventListener("mousedown", close); };
    }, [menu]);

    const openContextMenu = (event, row) => {
        const actions = actionsForRow(row).filter(action => !action.disabled);
        if (!actions.length) return;
        event.preventDefault();
        setMenu({ row, actions, x: event.clientX, y: event.clientY });
    };
    const toggleSort = column => {
        if (!column.sortValue) return;
        setSort(current => ({ key: column.key, direction: current.key === column.key && current.direction === "asc" ? "desc" : "asc" }));
    };
    const renderMenu = () => menu && createPortal(
        <div ref={menuRef} role="menu" aria-label="Record actions" tabIndex={-1} className="fixed z-[100] min-w-44 overflow-hidden rounded-xl border border-gray-700 bg-gray-900 p-1.5 shadow-2xl shadow-black/50" style={{ left: Math.max(8, Math.min(menu.x, window.innerWidth - 190)), top: Math.max(8, Math.min(menu.y, window.innerHeight - menu.actions.length * 42 - 24)) }}>
            {menu.actions.map((action, index) => <button key={action.label} type="button" role="menuitem" disabled={action.disabled} autoFocus={index === 0} onClick={() => { if (action.disabled) return; setMenu(null); action.onSelect(); requestAnimationFrame(() => menuTriggerRef.current?.focus()); }} className={`block w-full rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-gray-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-400 disabled:cursor-not-allowed disabled:opacity-50 ${action.danger ? "text-red-300" : "text-gray-200"}`}>{action.label}</button>)}
        </div>, document.body
    );

    return <section className="df-record-table">
        {showSearch && <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-800 p-4">
            <label className="sr-only" htmlFor={searchId}>{searchLabel}</label>
            <input id={searchId} type="search" value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} placeholder={searchPlaceholder} className="df-input w-full px-3.5 py-2.5 text-sm sm:max-w-sm" />
            <p className="text-xs text-gray-500" aria-live="polite">{filtered.length} {filtered.length === 1 ? "record" : "records"}</p>
        </div>}
        {filtered.length === 0 ? <p className="px-5 py-12 text-center text-sm text-gray-400">{emptyLabel}</p> : <>
            <div className="overflow-x-auto"><table className="df-table min-w-full">
                <thead><tr>{columns.map(column => <th key={column.key} scope="col" className={column.sortValue ? "select-none" : ""}>{column.sortValue ? <button type="button" onClick={() => toggleSort(column)} className="inline-flex items-center gap-1.5 hover:text-gray-200">{column.label}<span aria-hidden="true">{sort.key === column.key ? sort.direction === "asc" ? "↑" : "↓" : "↕"}</span></button> : column.label}</th>)}<th scope="col" className="text-right">Actions</th></tr></thead>
                <tbody>{visibleRows.map(row => {
                    const actions = actionsForRow(row);
                    return <tr key={getRowId(row)} onContextMenu={event => openContextMenu(event, row)} onClick={onRowClick ? () => onRowClick(row) : undefined} className={onRowClick ? "cursor-pointer" : ""}>
                        {columns.map(column => <td key={column.key}>{column.render ? column.render(row) : column.sortValue?.(row) ?? "—"}</td>)}
                        <td className="text-right"><div className="relative inline-block">{actions.length > 0 ? <button type="button" aria-label={`Actions for ${getSearchText(row) || "record"}`} aria-haspopup="menu" aria-expanded={menu?.row === row} onClick={event => { event.stopPropagation(); menuTriggerRef.current = event.currentTarget; setMenu(menu?.row === row ? null : { row, actions, x: event.currentTarget.getBoundingClientRect().right, y: event.currentTarget.getBoundingClientRect().bottom }); }} className="rounded-lg border border-gray-700 px-2.5 py-1.5 text-lg leading-none text-gray-300 hover:border-blue-500 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-400">⋯</button> : <span className="text-gray-600" aria-label="No available actions">—</span>}</div></td>
                    </tr>;
                })}</tbody>
            </table></div>
            <div className="flex items-center justify-between gap-3 border-t border-gray-800 px-4 py-3"><span className="text-xs text-gray-500" aria-live="polite">Page {safePage + 1} of {Math.max(pageCount, 1)}</span><div className="flex gap-2"><button type="button" onClick={() => setPage(current => Math.max(0, current - 1))} disabled={safePage === 0} className="df-button-secondary px-3 py-1.5 text-xs disabled:opacity-40">Previous</button><button type="button" onClick={() => setPage(current => Math.min(pageCount - 1, current + 1))} disabled={safePage + 1 >= pageCount} className="df-button-secondary px-3 py-1.5 text-xs disabled:opacity-40">Next</button></div></div>
        </>}{renderMenu()}
    </section>;
}
