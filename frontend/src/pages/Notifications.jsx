import { useEffect, useMemo, useState } from "react";
import { getNotifications, normalizeNotifications, markAllNotificationsAsRead, markNotificationAsRead } from "../services/notificationService";
import { connectWebSocket } from "../services/websocketService";
import Avatar from "../components/Avatar";

const isRead = notification => notification.read ?? notification.isRead ?? Boolean(notification.readAt);
const keyOf = notification => notification?.id != null ? `id:${notification.id}` : `${notification?.type || ""}|${notification?.message || ""}|${notification?.createdAt || ""}`;
const dateLabel = value => value && !Number.isNaN(new Date(value).getTime()) ? new Date(value).toLocaleString() : "Time unavailable";

function Notifications() {
    const [notifications, setNotifications] = useState([]);
    const [filter, setFilter] = useState("ALL");
    const [search, setSearch] = useState("");
    const [loading, setLoading] = useState(true);
    const [busyId, setBusyId] = useState(null);
    const [markingAll, setMarkingAll] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        getNotifications().then(data => {
            if (!active) return;
            const loaded = normalizeNotifications(data);
            setNotifications(previous => {
                const loadedKeys = new Set(loaded.map(keyOf));
                return [...previous.filter(item => !loadedKeys.has(keyOf(item))), ...loaded];
            });
        })
            .catch(reason => { if (active) setError(reason.message || "Could not load notifications."); })
            .finally(() => { if (active) setLoading(false); });
        const unsubscribe = connectWebSocket(notification => {
            const incoming = normalizeNotifications([notification])[0];
            if (!incoming) return;
            setNotifications(previous => previous.some(item => keyOf(item) === keyOf(incoming)) ? previous : [incoming, ...previous]);
        });
        return () => { active = false; unsubscribe?.(); };
    }, []);

    const unread = notifications.filter(notification => !isRead(notification)).length;
    const read = notifications.length - unread;
    const filtered = useMemo(() => notifications.filter(notification => {
        const matchesFilter = filter === "ALL" || (filter === "UNREAD" ? !isRead(notification) : isRead(notification));
        const haystack = `${notification.message || ""} ${notification.type || "Notification"}`.toLowerCase();
        return matchesFilter && haystack.includes(search.toLowerCase());
    }), [notifications, filter, search]);

    const markOne = async notification => {
        if (isRead(notification) || notification.id == null) return;
        setBusyId(notification.id);
        setError("");
        try {
            await markNotificationAsRead(notification.id);
            setNotifications(previous => previous.map(item => item.id === notification.id ? { ...item, read: true, isRead: true } : item));
        } catch (reason) { setError(reason.message || "Could not mark notification as read."); }
        finally { setBusyId(null); }
    };

    const markAll = async () => {
        if (!unread) return;
        setMarkingAll(true);
        setError("");
        try {
            await markAllNotificationsAsRead();
            setNotifications(previous => previous.map(item => ({ ...item, read: true, isRead: true })));
        } catch (reason) { setError(reason.message || "Could not mark notifications as read."); }
        finally { setMarkingAll(false); }
    };

    return <div className="mx-auto max-w-5xl space-y-7">
        <header className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-medium text-blue-400">WORKSPACE UPDATES</p><h1 className="mt-2 text-3xl font-semibold text-white">Notifications</h1><p className="mt-2 text-sm text-gray-400">Stay up to date with activity across your workspace.</p></div><button type="button" onClick={markAll} disabled={!unread || markingAll} className="rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">{markingAll ? "Marking…" : "Mark all as read"}</button></header>
        {error && <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">{error}</div>}

        <section className="grid gap-4 sm:grid-cols-3"><Stat label="Total notifications" value={notifications.length} /><Stat label="Unread" value={unread} accent="blue" /><Stat label="Read" value={read} /></section>

        <section className="overflow-hidden rounded-2xl border border-gray-800 bg-gray-900">
            <div className="flex flex-col gap-4 border-b border-gray-800 p-5 sm:flex-row sm:items-center sm:justify-between"><div className="flex flex-wrap gap-2" role="tablist" aria-label="Notification filters">{[["ALL", "All"], ["UNREAD", "Unread"], ["READ", "Read"]].map(([key, label]) => <button key={key} type="button" role="tab" aria-selected={filter === key} onClick={() => setFilter(key)} className={`rounded-lg px-3.5 py-2 text-sm font-medium transition ${filter === key ? "bg-blue-600 text-white" : "text-gray-400 hover:bg-gray-800 hover:text-white"}`}>{label}<span className="ml-2 text-xs opacity-70">{key === "ALL" ? notifications.length : key === "UNREAD" ? unread : read}</span></button>)}</div><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search notifications…" aria-label="Search notifications" className="w-full rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none placeholder:text-gray-500 focus:border-blue-500 sm:max-w-xs" /></div>
            {loading ? <div className="p-12 text-center text-sm text-gray-400">Loading notifications…</div> : filtered.length === 0 ? <div className="p-12 text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-gray-800 text-xl">♧</div><h2 className="mt-4 font-medium text-white">{notifications.length ? "No matching notifications" : "You're all caught up"}</h2><p className="mt-2 text-sm text-gray-500">{notifications.length ? "Try another filter or search term." : "New workspace activity will appear here."}</p></div> : <div className="divide-y divide-gray-800">{filtered.map((notification, index) => {
                const readState = isRead(notification);
                return <article key={keyOf(notification) + `:${index}`} className={`flex gap-4 p-5 transition ${readState ? "" : "bg-gray-800/30"}`}><div className="flex w-3 shrink-0 justify-center pt-1.5">{!readState && <span className="h-2.5 w-2.5 rounded-full bg-blue-500 ring-4 ring-blue-500/10" />}</div><Avatar name="Notification recipient" src={notification.recipientAvatarUrl} size={36} /><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><span className="rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-[11px] font-medium text-blue-300">{notification.type || "Notification"}</span><span className={`text-xs ${readState ? "text-gray-500" : "text-blue-300"}`}>{readState ? "Read" : "Unread"}</span></div><p className={`mt-3 text-sm leading-relaxed ${readState ? "text-gray-300" : "font-medium text-white"}`}>{notification.message || "A workspace update is available."}</p><p className="mt-2 text-xs text-gray-500">{dateLabel(notification.createdAt || notification.timestamp)}</p></div>{!readState && <button type="button" disabled={busyId === notification.id || notification.id == null} onClick={() => markOne(notification)} className="self-center whitespace-nowrap rounded-lg border border-gray-700 px-3 py-2 text-xs font-medium text-gray-300 hover:border-blue-500 hover:text-blue-300 disabled:opacity-50">{busyId === notification.id ? "Saving…" : "Mark read"}</button>}</article>;
            })}</div>}
        </section>
    </div>;
}

function Stat({ label, value, accent }) { return <div className="rounded-2xl border border-gray-800 bg-gray-900 p-5"><p className="text-sm text-gray-500">{label}</p><p className={`mt-3 text-3xl font-semibold ${accent ? "text-blue-300" : "text-white"}`}>{value}</p></div>; }

export default Notifications;
