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
        onStatusChange(true);

        stompClient.subscribe("/topic/location", (message) => {
            const location = JSON.parse(message.body);
            onMessage(location);
        });
    };

    stompClient.onDisconnect = () => {
        onStatusChange(false);
    };

    stompClient.activate();
}

export function fetchInitialLocations() {
    return fetch(`${TRACKING_SERVICE_URL}/tracking/ambulances`).then((res) =>
        res.json()
    );
}
