import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { notificationTargetPath } from "../utils/notificationNavigation";
import {
    getNotificationPage,
    getUnreadNotificationCount,
    normalizeNotifications,
    markNotificationAsRead,
    markAllNotificationsAsRead
} from "../services/notificationService";
import { connectWebSocket } from "../services/websocketService";
import Avatar from "./Avatar";

const notificationKey = notification => notification?.id != null ? `id:${notification.id}` : `${notification?.type || ""}|${notification?.message || ""}|${notification?.createdAt || ""}`;
const formatNotificationType = value => String(value || "Notification").replace(/^ISSUE_/, "TASK_").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
const formatNotificationMessage = value => String(value || "A workspace update is available.").replace(/\bissues?\b/gi, word => `${word[0] === word[0].toUpperCase() ? "T" : "t"}ask${word.toLowerCase().endsWith("s") ? "s" : ""}`);

function NotificationBell() {
    const [notifications, setNotifications] = useState([]);
    const [open, setOpen] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const navigate = useNavigate();
    const seenKeys = useRef(new Set());
    const countRequest = useRef(0);

    const refreshUnreadCount = useCallback(async () => {
        const requestId = ++countRequest.current;
        try {
            const count = await getUnreadNotificationCount();
            if (requestId === countRequest.current) setUnreadCount(Number(count) || 0);
        } catch (error) {
            console.error("Failed to load unread notification count:", error);
        }
    }, []);

    useEffect(() => {
        let active = true;
        getNotificationPage({ page: 0, size: 10 })
            .then(data => {
                if (!active) return;
                setNotifications(previous => {
                    const loaded = normalizeNotifications(data?.content);
                    loaded.forEach(item => seenKeys.current.add(notificationKey(item)));
                    const loadedKeys = new Set(loaded.map(notificationKey));
                    return [...previous.filter(item => !loadedKeys.has(notificationKey(item))), ...loaded];
                });
            })
            .catch(error => console.error("Failed to load notifications:", error));
        getUnreadNotificationCount()
            .then(count => { if (active) setUnreadCount(Number(count) || 0); })
            .catch(error => console.error("Failed to load unread notification count:", error));

        const unsubscribe = connectWebSocket((notification) => {
            if (notification?.event === "READ") {
                setNotifications(previous => previous.map(item => item.id === notification.notificationId ? { ...item, read: true } : item));
                void refreshUnreadCount();
                return;
            }
            if (notification?.event === "READ_ALL") {
                setNotifications(previous => previous.map(item => ({ ...item, read: true })));
                setUnreadCount(0);
                void refreshUnreadCount();
                return;
            }
            const incoming = normalizeNotifications([notification])[0];
            if (!incoming) return;
            const key = notificationKey(incoming);
            if (seenKeys.current.has(key)) return;
            seenKeys.current.add(key);
            window.dispatchEvent(new Event("devflow-notification-received"));
            setNotifications(prev => [incoming, ...prev].slice(0, 10));
            if (!incoming.read) setUnreadCount(count => count + 1);
            refreshUnreadCount();
        });

        const onConnected = () => {
            void refreshUnreadCount();
            getNotificationPage({ page: 0, size: 10 })
                .then(data => {
                    if (!active) return;
                    const loaded = normalizeNotifications(data?.content);
                    loaded.forEach(item => seenKeys.current.add(notificationKey(item)));
                    setNotifications(previous => {
                        const merged = new Map();
                        [...previous, ...loaded].forEach(item => merged.set(notificationKey(item), item));
                        return [...merged.values()].sort((a, b) =>
                            new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
                        ).slice(0, 10);
                    });
                })
                .catch(error => console.error("Failed to recover notifications after reconnect:", error));
        };
        window.addEventListener("devflow-websocket-connected", onConnected);

        return () => {
            active = false;
            window.removeEventListener("devflow-websocket-connected", onConnected);
            unsubscribe?.();
        };
    }, [refreshUnreadCount]);

    const handleOpen = async notification => {
        if (!notification.read) await handleRead(notification.id);
        setOpen(false);
        const target = notificationTargetPath(notification.targetPath);
        if (target) navigate(target);
    };

    const handleRead = async (id) => {
        if (id == null) return;
        try {
            await markNotificationAsRead(id);
            const notification = notifications.find(item => item.id === id);
            setNotifications(prev =>
                prev.map(notification =>
                    notification.id === id
                        ? { ...notification, read: true }
                        : notification
                )
            );
            if (notification && !notification.read) {
                setUnreadCount(count => Math.max(0, count - 1));
                refreshUnreadCount();
            }
        } catch (error) {
            console.error("Failed to mark notification as read:", error);
        }
    };

    const handleReadAll = async () => {
        try {
            await markAllNotificationsAsRead();
            setNotifications(prev =>
                prev.map(notification => ({ ...notification, read: true }))
            );
            setUnreadCount(0);
            refreshUnreadCount();
        } catch (error) {
            console.error("Failed to mark notifications as read:", error);
        }
    };

    return (
        <div className="dfx-notification-control relative">
            <button
                onClick={() => setOpen(!open)}
                aria-label="Notifications" aria-expanded={open} className="dfx-notification-trigger relative rounded-lg p-2 text-gray-300 hover:bg-gray-800 hover:text-white"
            >
                <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-5 w-5"><path strokeLinecap="round" strokeLinejoin="round" d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>

                {unreadCount > 0 && (
                    <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
                        {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                )}
            </button>

            {open && (
                <div className="dfx-notification-popover absolute right-0 z-50 mt-3 w-96 rounded-xl border border-gray-700 bg-gray-900 shadow-2xl">
                    <div className="flex items-center justify-between border-b border-gray-700 p-4">
                        <h3 className="font-semibold text-white">
                            Notifications
                        </h3>

                        {unreadCount > 0 && (
                            <button
                                onClick={handleReadAll}
                                className="text-xs text-blue-400 hover:text-blue-300"
                            >
                                Mark all as read
                            </button>
                        )}
                    </div>

                    <div className="max-h-96 overflow-y-auto">
                        {notifications.length === 0 ? (
                            <p className="p-6 text-center text-sm text-gray-400">
                                No notifications
                            </p>
                        ) : (
                            notifications.map(notification => (
                                <button
                                    key={notification.id}
                                    onClick={() => void handleOpen(notification)}
                                    className={`w-full border-b border-gray-800 p-4 text-left hover:bg-gray-800 ${
                                        !notification.read ? "bg-gray-800/60" : ""
                                    }`}
                                >
                                    <div className="flex items-start gap-3"><Avatar name="Notification recipient" src={notification.recipientAvatarUrl} size={32} />
                                        {!notification.read && (
                                            <span className="mt-1 h-2 w-2 rounded-full bg-blue-500" />
                                        )}

                                        <div className="flex-1">
                                            <p className="text-sm text-gray-200">
                                                {formatNotificationMessage(notification.message)}
                                            </p>

                                            <p className="mt-1 text-xs text-gray-500">
                                                {formatNotificationType(notification.type)}
                                            </p>
                                            {notification.createdAt && !Number.isNaN(new Date(notification.createdAt).getTime()) && <p className="mt-1 text-xs text-gray-600">{new Date(notification.createdAt).toLocaleString()}</p>}
                                        </div>
                                    </div>
                                </button>
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

export default NotificationBell;
