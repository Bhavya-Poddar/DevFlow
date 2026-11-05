import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { notificationTargetPath } from "../utils/notificationNavigation";
import {
    getNotificationPage,
    getUnreadNotificationCount,
    markAllNotificationsAsRead,
    markNotificationAsRead,
    normalizeNotifications
} from "../services/notificationService";
import { connectWebSocket } from "../services/websocketService";
import Avatar from "../components/Avatar";
import RecordTable from "../components/RecordTable";

const PAGE_SIZE = 20;
const isRead = notification => notification.read ?? notification.isRead ?? Boolean(notification.readAt);
const keyOf = notification => notification?.id != null ? `id:${notification.id}` : `${notification?.type || ""}|${notification?.message || ""}|${notification?.createdAt || ""}`;
const dateLabel = value => value && !Number.isNaN(new Date(value).getTime()) ? new Date(value).toLocaleString() : "Time unavailable";
const formatNotificationType = value => String(value || "Notification").replace(/^ISSUE_/, "TASK_").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
const formatNotificationMessage = value => String(value || "A workspace update is available.").replace(/\bissues?\b/gi, word => `${word[0] === word[0].toUpperCase() ? "T" : "t"}ask${word.toLowerCase().endsWith("s") ? "s" : ""}`);

function Notifications() {
    const [notifications, setNotifications] = useState([]);
    const [filter, setFilter] = useState("ALL");
    const [search, setSearch] = useState("");
    const [pageNumber, setPageNumber] = useState(0);
    const [pageData, setPageData] = useState(null);
    const [unreadCount, setUnreadCount] = useState(0);
    const [busyId, setBusyId] = useState(null);
    const [markingAll, setMarkingAll] = useState(false);
    const [actionError, setActionError] = useState("");
    const [refreshVersion, setRefreshVersion] = useState(0);
    const countRequest = useRef(0);
    const seenKeys = useRef(new Set());
    const queryKey = `${pageNumber}|${filter}|${search}|${refreshVersion}`;
    const [pageResult, setPageResult] = useState(null);
    const navigate = useNavigate();

    const refreshUnreadCount = useCallback(async () => {
        const requestId = ++countRequest.current;
        try {
            const count = await getUnreadNotificationCount();
            if (requestId === countRequest.current) setUnreadCount(Number(count) || 0);
        } catch (reason) {
            if (requestId === countRequest.current) setActionError(reason.message || "Could not load unread count.");
        }
    }, []);

    useEffect(() => {
        let active = true;
        const read = filter === "UNREAD" ? false : filter === "READ" ? true : undefined;
        getNotificationPage({ page: pageNumber, size: PAGE_SIZE, read, search })
            .then(data => {
                if (!active) return;
                setPageData(data);
                const loaded = normalizeNotifications(data?.content);
                loaded.forEach(item => seenKeys.current.add(keyOf(item)));
                setNotifications(loaded);
                setPageResult({ key: queryKey, error: "" });
            })
            .catch(reason => { if (active) setPageResult({ key: queryKey, error: reason.message || "Could not load notifications." }); });
        return () => { active = false; };
    }, [filter, search, pageNumber, refreshVersion, queryKey]);

    useEffect(() => {
        const requestId = ++countRequest.current;
        getUnreadNotificationCount()
            .then(count => { if (requestId === countRequest.current) setUnreadCount(Number(count) || 0); })
            .catch(reason => { if (requestId === countRequest.current) setActionError(reason.message || "Could not load unread count."); });
        const unsubscribe = connectWebSocket(notification => {
            if (notification?.event === "READ") {
                setNotifications(previous => previous.map(item => item.id === notification.notificationId ? { ...item, read: true, isRead: true } : item));
                setRefreshVersion(value => value + 1);
                void refreshUnreadCount();
                return;
            }
            if (notification?.event === "READ_ALL") {
                setNotifications(previous => previous.map(item => ({ ...item, read: true, isRead: true })));
                setRefreshVersion(value => value + 1);
                void refreshUnreadCount();
                return;
            }
            const incoming = normalizeNotifications([notification])[0];
            if (!incoming || isRead(incoming) || pageNumber !== 0) return;
            const key = keyOf(incoming);
            if (seenKeys.current.has(key)) return;
            seenKeys.current.add(key);
            const matchesRead = filter !== "READ";
            const matchesSearch = !search.trim()
                || `${incoming.message || ""} ${incoming.type || ""}`.toLowerCase().includes(search.trim().toLowerCase());
            if (matchesRead && matchesSearch) {
                setNotifications(previous => previous.some(item => keyOf(item) === keyOf(incoming))
                    ? previous : [incoming, ...previous].slice(0, PAGE_SIZE));
                setPageData(previous => previous ? { ...previous, totalElements: previous.totalElements + 1 } : previous);
            }
            setUnreadCount(count => count + 1);
            refreshUnreadCount();
        });
        const onConnected = () => {
            setRefreshVersion(value => value + 1);
            void refreshUnreadCount();
        };
        window.addEventListener("devflow-websocket-connected", onConnected);
        return () => {
            window.removeEventListener("devflow-websocket-connected", onConnected);
            unsubscribe?.();
        };
    }, [pageNumber, filter, search, refreshUnreadCount]);

    const filtered = notifications;
    const loading = pageResult?.key !== queryKey;
    const error = loading ? "" : pageResult?.error || actionError;
    const matchingCount = pageData?.totalElements ?? 0;
    const totalPages = pageData?.totalPages ?? 0;

    const markOne = async notification => {
        if (isRead(notification) || notification.id == null) return;
        setBusyId(notification.id);
        setActionError("");
        try {
            await markNotificationAsRead(notification.id);
            setNotifications(previous => previous.map(item => item.id === notification.id ? { ...item, read: true, isRead: true } : item));
            setUnreadCount(count => Math.max(0, count - 1));
            await refreshUnreadCount();
            if (filter === "UNREAD") setRefreshVersion(value => value + 1);
        } catch (reason) { setActionError(reason.message || "Could not mark notification as read."); }
        finally { setBusyId(null); }
    };

    const openRelated = async notification => {
        if (!isRead(notification)) await markOne(notification);
        const target = notificationTargetPath(notification.targetPath);
        if (target) navigate(target);
    };

    const markAll = async () => {
        if (!unreadCount) return;
        setMarkingAll(true);
        setActionError("");
        try {
            await markAllNotificationsAsRead();
            setNotifications(previous => previous.map(item => ({ ...item, read: true, isRead: true })));
            setUnreadCount(0);
            await refreshUnreadCount();
            if (filter === "UNREAD") setRefreshVersion(value => value + 1);
        } catch (reason) { setActionError(reason.message || "Could not mark notifications as read."); }
        finally { setMarkingAll(false); }
    };

    return <div className="dfx-screen dfx-screen--notifications mx-auto max-w-5xl space-y-7">
        <header className="dfx-screen-header flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-medium text-blue-400">WORKSPACE UPDATES</p><h1 className="mt-2 text-3xl font-semibold text-white">Notifications</h1><p className="mt-2 text-sm text-gray-400">Stay up to date with activity across your workspace.</p></div><button type="button" onClick={markAll} disabled={!unreadCount || markingAll} className="rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">{markingAll ? "Marking…" : "Mark all as read"}</button></header>
        {error && <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">{error}</div>}

        <section className="grid gap-4 sm:grid-cols-2"><Stat label="Matching notifications" value={matchingCount} /><Stat label="Unread in your account" value={unreadCount} accent="blue" /></section>

        <section className="overflow-hidden rounded-2xl border border-gray-800 bg-gray-900">
            <div className="flex flex-col gap-4 border-b border-gray-800 p-5 sm:flex-row sm:items-center sm:justify-between"><div className="flex flex-wrap gap-2" role="tablist" aria-label="Notification filters">{[["ALL", "All"], ["UNREAD", "Unread"], ["READ", "Read"]].map(([key, label]) => <button key={key} type="button" role="tab" aria-selected={filter === key} onClick={() => { setFilter(key); setPageNumber(0); }} className={`rounded-lg px-3.5 py-2 text-sm font-medium transition ${filter === key ? "bg-blue-600 text-white" : "text-gray-400 hover:bg-gray-800 hover:text-white"}`}>{label}</button>)}</div><input value={search} onChange={event => { setSearch(event.target.value); setPageNumber(0); }} placeholder="Search notifications…" aria-label="Search notifications" className="w-full rounded-lg border border-gray-700 bg-gray-950 px-4 py-2.5 text-sm text-white outline-none placeholder:text-gray-500 focus:border-blue-500 sm:max-w-xs" /></div>
            {loading ? <div className="p-12 text-center text-sm text-gray-400" role="status">Loading notifications…</div> : filtered.length === 0 ? <div className="p-12 text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-gray-800 text-xl">♧</div><h2 className="mt-4 font-medium text-white">{search || filter !== "ALL" ? "No matching notifications" : "You're all caught up"}</h2><p className="mt-2 text-sm text-gray-500">{search || filter !== "ALL" ? "Try another filter or search term." : "New workspace activity will appear here."}</p></div> : <RecordTable rows={filtered} getRowId={notification => keyOf(notification)} showSearch={false} pageSize={PAGE_SIZE} emptyLabel="No notifications found." getSearchText={notification => `${notification.type || ""} ${notification.message || ""}`} columns={[
                { key: "type", label: "Type", sortValue: notification => notification.type, render: notification => <span className="rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-[11px] font-medium text-blue-300">{formatNotificationType(notification.type)}</span> },
                { key: "message", label: "Notification", sortValue: notification => notification.message, render: notification => <div className="flex min-w-56 items-center gap-3"><Avatar name="Notification recipient" src={notification.recipientAvatarUrl} size={32} /><span className="max-w-2xl text-sm text-gray-200">{formatNotificationMessage(notification.message)}</span></div> },
                { key: "read", label: "State", sortValue: notification => isRead(notification) ? 1 : 0, render: notification => <span className={isRead(notification) ? "text-xs text-gray-500" : "text-xs font-medium text-blue-300"}>{isRead(notification) ? "Read" : "Unread"}</span> },
                { key: "createdAt", label: "Received", sortValue: notification => new Date(notification.createdAt || notification.timestamp || 0).getTime(), render: notification => <time className="whitespace-nowrap text-xs text-gray-500">{dateLabel(notification.createdAt || notification.timestamp)}</time> }
            ]} actionsForRow={notification => [
                ...(notificationTargetPath(notification.targetPath) ? [{ label: "Open related item", onSelect: () => void openRelated(notification) }] : []),
                ...(!isRead(notification) ? [{ label: busyId === notification.id ? "Saving…" : "Mark as read", disabled: busyId === notification.id || notification.id == null, onSelect: () => void markOne(notification) }] : [])
            ]} />}
            {!loading && totalPages > 1 && <nav aria-label="Notification history pages" className="flex items-center justify-between border-t border-gray-800 p-4"><span className="text-xs text-gray-500">Page {pageNumber + 1} of {totalPages}</span><div className="flex gap-2"><button type="button" onClick={() => setPageNumber(value => Math.max(0, value - 1))} disabled={pageNumber === 0} className="rounded-lg border border-gray-700 px-3 py-2 text-xs text-gray-300 disabled:opacity-40">Newer</button><button type="button" onClick={() => setPageNumber(value => value + 1)} disabled={pageNumber + 1 >= totalPages} className="rounded-lg border border-gray-700 px-3 py-2 text-xs text-gray-300 disabled:opacity-40">Older</button></div></nav>}
        </section>
    </div>;
}

function Stat({ label, value, accent }) { return <div className="rounded-2xl border border-gray-800 bg-gray-900 p-5"><p className="text-sm text-gray-500">{label}</p><p className={`mt-3 text-3xl font-semibold ${accent ? "text-blue-300" : "text-white"}`}>{value}</p></div>; }

export default Notifications;
