import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const TRACKING_SERVICE_URL = "http://localhost:8085";
const WS_ENDPOINT = `${TRACKING_SERVICE_URL}/ws-sockjs`;

let stompClient = null;

export function connectWebSocket(onMessage, onStatusChange) {
    stompClient = new Client({
        brokerURL: WS_ENDPOINT,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: () => { }, // Disable debug logs
        webSocketFactory: () => new SockJS(WS_ENDPOINT),
    });

    stompClient.onConnect = () => {
        console.log("WebSocket connected");
        onStatusChange(true);

        // Wait a bit to ensure connection is fully established
        setTimeout(() => {
            if (stompClient && stompClient.connected) {
                stompClient.subscribe("/topic/location", (message) => {
                    const location = JSON.parse(message.body);
                    onMessage(location);
                });
            }
        }, 100);
    };

    stompClient.onDisconnect = () => {
        console.log("WebSocket disconnected");
        onStatusChange(false);
    };

    stompClient.onStompError = (frame) => {
        console.error("STOMP error:", frame);
        onStatusChange(false);
    };

    stompClient.activate();
}

export function disconnectWebSocket() {
    if (stompClient && stompClient.connected) {
        stompClient.deactivate();
    }
}

export function fetchInitialLocations() {
    return fetch(`${TRACKING_SERVICE_URL}/tracking/ambulances`)
        .then((res) => {
            if (!res.ok) {
                throw new Error(`HTTP error! status: ${res.status}`);
            }
            return res.json();
        })
        .catch((error) => {
            console.error("Failed to fetch initial locations:", error);
            return {}; // Return empty object on error
        });
}
