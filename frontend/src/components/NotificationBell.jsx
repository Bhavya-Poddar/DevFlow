import { useEffect, useState } from "react";
import {
    getNotifications,
    normalizeNotifications,
    markNotificationAsRead,
    markAllNotificationsAsRead
} from "../services/notificationService";
import { connectWebSocket } from "../services/websocketService";
import Avatar from "./Avatar";

const notificationKey = notification => notification?.id != null ? `id:${notification.id}` : `${notification?.type || ""}|${notification?.message || ""}|${notification?.createdAt || ""}`;

function NotificationBell() {
    const [notifications, setNotifications] = useState([]);
    const [open, setOpen] = useState(false);

    const unreadCount = notifications.filter(notification => !notification.read).length;

    useEffect(() => {
        let active = true;
        getNotifications()
            .then(data => {
                if (!active) return;
                setNotifications(previous => {
                    const loaded = normalizeNotifications(data);
                    const loadedKeys = new Set(loaded.map(notificationKey));
                    return [...previous.filter(item => !loadedKeys.has(notificationKey(item))), ...loaded];
                });
            })
            .catch(error => console.error("Failed to load notifications:", error));

        const unsubscribe = connectWebSocket((notification) => {
            const incoming = normalizeNotifications([notification])[0];
            if (!incoming) return;
            setNotifications(prev => {
                if (prev.some(item => notificationKey(item) === notificationKey(incoming))) return prev;
                return [incoming, ...prev];
            });
        });

        return () => {
            active = false;
            unsubscribe?.();
        };
    }, []);

    const handleRead = async (id) => {
        if (id == null) return;
        try {
            await markNotificationAsRead(id);
            setNotifications(prev =>
                prev.map(notification =>
                    notification.id === id
                        ? { ...notification, read: true }
                        : notification
                )
            );
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
        } catch (error) {
            console.error("Failed to mark notifications as read:", error);
        }
    };

    return (
        <div className="relative">
            <button
                onClick={() => setOpen(!open)}
                className="relative rounded-lg p-2 text-gray-300 hover:bg-gray-800 hover:text-white"
            >
                🔔

                {unreadCount > 0 && (
                    <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
                        {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                )}
            </button>

            {open && (
                <div className="absolute right-0 z-50 mt-3 w-96 rounded-xl border border-gray-700 bg-gray-900 shadow-2xl">
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
                                    onClick={() => handleRead(notification.id)}
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
                                                {notification.message || "A workspace update is available."}
                                            </p>

                                            <p className="mt-1 text-xs text-gray-500">
                                                {notification.type || "Notification"}
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
