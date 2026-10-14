import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

let client = null;
const subscribers = new Set();

const stopClient = () => {
    if (client) {
        client.deactivate();
        client = null;
    }
};

export const connectWebSocket = (onNotification) => {
    if (typeof onNotification === "function") subscribers.add(onNotification);
    if (client) return () => disconnectWebSocket(onNotification);

    const token = localStorage.getItem("token");
    if (!token) return () => disconnectWebSocket(onNotification);

    client = new Client({
        webSocketFactory: () => new SockJS("http://localhost:8080/ws"),
        connectHeaders: {
            Authorization: `Bearer ${token}`
        },
        reconnectDelay: 5000,
        onConnect: () => {
            console.log("WebSocket connected");

            client.subscribe("/user/queue/notifications", (message) => {
                const notification = JSON.parse(message.body);
                subscribers.forEach(callback => callback(notification));
            });
        },
        onStompError: (frame) => {
            console.error("WebSocket error:", frame.headers["message"]);
        },
        onWebSocketError: (error) => {
            console.error("WebSocket connection error:", error);
        }
    });

    client.activate();
    return () => disconnectWebSocket(onNotification);
};

export const disconnectWebSocket = (onNotification) => {
    if (typeof onNotification === "function") {
        subscribers.delete(onNotification);
        if (subscribers.size) return;
    } else {
        subscribers.clear();
    }
    stopClient();
};

