import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * WebSocket service for managing STOMP connections with JWT authentication.
 * Provides methods to connect, disconnect, subscribe to topics, and check connection status.
 */
class WebSocketService {
  constructor() {
    this.client = null;
    this.subscriptions = new Map();
    this.isConnecting = false;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 5000;
    this.connectionState = 'disconnected';
    this.connectionStateListeners = new Set();
  }

  setConnectionState(state) {
    this.connectionState = state;
    this.connectionStateListeners.forEach((listener) => {
      try {
        listener(state);
      } catch (_err) {
        // Ignore listener errors to avoid breaking WS flow
      }
    });
  }

  onConnectionStateChange(listener) {
    this.connectionStateListeners.add(listener);
    listener(this.connectionState);
    return () => this.connectionStateListeners.delete(listener);
  }

  /**
   * Connect to the WebSocket server with JWT authentication.
   * @param {string} token - JWT access token for authentication
   * @param {Function} onTokenRefreshNeeded - Callback to trigger token refresh on auth failure
   * @returns {Promise<void>} Resolves when connection is established
   */
  async connect(token, onTokenRefreshNeeded = null) {
    if (this.client?.connected) {
      return Promise.resolve();
    }

    if (this.isConnecting) {
      return Promise.resolve();
    }

    this.isConnecting = true;
    this.setConnectionState('connecting');

    return new Promise((resolve, reject) => {
      try {
        const wsToken = encodeURIComponent(token);

        const socket = new SockJS(`http://localhost:8085/ws?token=${wsToken}`);

        this.client = new Client({
          // Tracking service is configured with SockJS endpoints.
          webSocketFactory: () => socket,
          connectHeaders: {},
          reconnectDelay: this.reconnectDelay,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
          debug: () => {
            // Uncomment for debugging
            // console.log('STOMP Debug:', str);
          },
        });

        this.client.onConnect = () => {
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          this.setConnectionState('connected');
          resolve();
        };

        this.client.onStompError = (frame) => {
          this.isConnecting = false;
          this.setConnectionState('error');

          if (frame.headers['message']?.includes('401') ||
              frame.headers['message']?.includes('Unauthorized')) {
            if (onTokenRefreshNeeded) {
              onTokenRefreshNeeded();
            }
          }

          reject(new Error(frame.headers['message'] || 'WebSocket connection failed'));
        };

        this.client.onWebSocketError = (error) => {
          this.isConnecting = false;
          this.setConnectionState('error');
          reject(error);
        };

        this.client.onDisconnect = () => {
          this.isConnecting = false;
          this.setConnectionState('disconnected');
        };

        this.client.activate();
      } catch (error) {
        this.isConnecting = false;
        this.setConnectionState('error');
        reject(error);
      }
    });
  }

  /**
   * Disconnect from the WebSocket server and clean up all subscriptions.
   */
  disconnect() {
    if (!this.client) {
      return;
    }

    this.subscriptions.forEach((subscription) => {
      try {
        subscription.unsubscribe();
      } catch (_error) {
        // No-op
      }
    });
    this.subscriptions.clear();

    try {
      this.client.deactivate();
    } catch (_error) {
      // No-op
    }

    this.client = null;
    this.isConnecting = false;
    this.setConnectionState('disconnected');
  }

  /**
   * Subscribe to a STOMP topic and receive messages.
   * @param {string} topic - The topic to subscribe to (e.g., '/topic/location')
   * @param {Function} callback - Callback function to handle received messages
   * @returns {Function} Unsubscribe function to stop receiving messages
   */
  subscribe(topic, callback) {
    if (!this.client?.connected) {
      throw new Error('WebSocket not connected. Call connect() first.');
    }

    if (this.subscriptions.has(topic)) {
      return () => {
        const subscription = this.subscriptions.get(topic);
        if (subscription) {
          subscription.unsubscribe();
          this.subscriptions.delete(topic);
        }
      };
    }

    try {
      const subscription = this.client.subscribe(topic, (message) => {
        try {
          const data = JSON.parse(message.body);
          callback(data);
        } catch (error) {
          callback(message.body);
        }
      });

      this.subscriptions.set(topic, subscription);

      return () => {
        try {
          subscription.unsubscribe();
          this.subscriptions.delete(topic);
        } catch (error) {
          // No-op
        }
      };
    } catch (error) {
      throw error;
    }
  }

  /**
   * Check if the WebSocket is currently connected.
   * @returns {boolean} True if connected, false otherwise
   */
  isConnected() {
    return this.client?.connected ?? false;
  }

  /**
   * Get the current connection state.
   * @returns {string} Connection state: 'connected', 'connecting', or 'disconnected'
   */
  getConnectionState() {
    return this.connectionState;
  }
}

export const wsService = new WebSocketService();
export { WebSocketService };
