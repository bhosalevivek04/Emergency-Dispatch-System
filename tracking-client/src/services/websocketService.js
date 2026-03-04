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
  }

  /**
   * Connect to the WebSocket server with JWT authentication.
   * @param {string} token - JWT access token for authentication
   * @param {Function} onTokenRefreshNeeded - Callback to trigger token refresh on auth failure
   * @returns {Promise<void>} Resolves when connection is established
   */
  async connect(token, onTokenRefreshNeeded = null) {
    if (this.client?.connected) {
      console.log('WebSocket already connected');
      return Promise.resolve();
    }

    if (this.isConnecting) {
      console.log('WebSocket connection already in progress');
      return Promise.resolve();
    }

    this.isConnecting = true;

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
          console.log('WebSocket connected successfully');
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          resolve();
        };

        this.client.onStompError = (frame) => {
          console.error('STOMP error:', frame.headers['message']);
          this.isConnecting = false;

          if (frame.headers['message']?.includes('401') ||
              frame.headers['message']?.includes('Unauthorized')) {
            console.log('WebSocket authentication failed, triggering token refresh');
            if (onTokenRefreshNeeded) {
              onTokenRefreshNeeded();
            }
          }

          reject(new Error(frame.headers['message'] || 'WebSocket connection failed'));
        };

        this.client.onWebSocketError = (error) => {
          console.error('WebSocket error:', error);
          this.isConnecting = false;
          reject(error);
        };

        this.client.onDisconnect = () => {
          console.log('WebSocket disconnected');
          this.isConnecting = false;
        };

        this.client.activate();
      } catch (error) {
        console.error('Error creating WebSocket connection:', error);
        this.isConnecting = false;
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
      } catch (error) {
        console.error('Error unsubscribing:', error);
      }
    });
    this.subscriptions.clear();

    try {
      this.client.deactivate();
    } catch (error) {
      console.error('Error deactivating WebSocket client:', error);
    }

    this.client = null;
    this.isConnecting = false;
    console.log('WebSocket disconnected and cleaned up');
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
      console.warn(`Already subscribed to topic: ${topic}`);
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
          console.error('Error parsing WebSocket message:', error);
          callback(message.body);
        }
      });

      this.subscriptions.set(topic, subscription);
      console.log(`Subscribed to topic: ${topic}`);

      return () => {
        try {
          subscription.unsubscribe();
          this.subscriptions.delete(topic);
          console.log(`Unsubscribed from topic: ${topic}`);
        } catch (error) {
          console.error('Error unsubscribing from topic:', error);
        }
      };
    } catch (error) {
      console.error(`Error subscribing to topic ${topic}:`, error);
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
    if (this.client?.connected) {
      return 'connected';
    }
    if (this.isConnecting) {
      return 'connecting';
    }
    return 'disconnected';
  }
}

export const wsService = new WebSocketService();
export { WebSocketService };
