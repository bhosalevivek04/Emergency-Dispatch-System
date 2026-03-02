# 🚑 Real-Time Ambulance Tracking Client

React-based real-time tracking interface using Leaflet, WebSocket (STOMP), and Kafka event streaming.

## Architecture

```
Kafka Producer (Driver App)
       ↓
Tracking Service (Consumer)
       ↓
Redis Cache
       ↓
WebSocket (/topic/location)
       ↓
React STOMP Client
       ↓
Leaflet Live Animation
```

## Features

✅ Real-time location updates via WebSocket  
✅ Smooth animated marker movement  
✅ Route planning with OSRM  
✅ Live connection status  
✅ Interactive map with OpenStreetMap  
✅ Ambulance and hospital markers  

## Prerequisites

- Node.js 16+
- Running tracking-service on port 8085
- Kafka and Redis running

## Installation

```bash
npm install
```

## Running

```bash
npm start
```

The app will open at `http://localhost:3000`

## Configuration

WebSocket endpoint is configured in `TrackingMap.jsx`:
```javascript
const socket = new SockJS("http://localhost:8085/ws");
```

Change this if your tracking-service runs on a different port.

## Components

### TrackingMap
Real-time tracking with WebSocket connection and smooth animation.

### RouteMap
Route planning between ambulance and hospital using OSRM routing.

## Tech Stack

- React 19
- Leaflet & React-Leaflet
- SockJS & STOMP
- Leaflet Routing Machine
- OpenStreetMap tiles

## Resume Upgrade

**Instead of:** "Built tracking microservice"

**Write:** "Designed and implemented a distributed real-time ambulance tracking system using Kafka, Redis, WebSocket (STOMP), and Leaflet with live route visualization and animated location streaming."

This demonstrates 3+ year backend/full-stack engineering experience.
