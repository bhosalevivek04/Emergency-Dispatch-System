# API Client Usage

## Overview

The `apiClient.js` module provides a configured Axios instance for making authenticated HTTP requests to the Emergency Dispatch System API Gateway.

## Features

- **Automatic JWT Token Attachment**: All requests automatically include the Bearer token in the Authorization header
- **Automatic Token Refresh**: When a 401 Unauthorized response is received, the client automatically refreshes the access token and retries the request
- **Centralized Configuration**: Base URL, timeout, and headers are configured in one place

## Setup

The API client is automatically initialized by the `AuthProvider` component. No manual setup is required.

## Usage Examples

### Basic GET Request

```javascript
import apiClient from '../services/apiClient';

// Fetch all emergencies
const fetchEmergencies = async () => {
  try {
    const response = await apiClient.get('/api/emergencies');
    return response.data;
  } catch (error) {
    console.error('Failed to fetch emergencies:', error);
    throw error;
  }
};
```

### POST Request

```javascript
import apiClient from '../services/apiClient';

// Create a new emergency
const createEmergency = async (emergencyData) => {
  try {
    const response = await apiClient.post('/api/emergencies', emergencyData);
    return response.data;
  } catch (error) {
    console.error('Failed to create emergency:', error);
    throw error;
  }
};
```

### PUT Request

```javascript
import apiClient from '../services/apiClient';

// Update emergency status
const updateEmergencyStatus = async (emergencyId, status) => {
  try {
    const response = await apiClient.put(
      `/api/emergencies/${emergencyId}/status`,
      { status }
    );
    return response.data;
  } catch (error) {
    console.error('Failed to update emergency status:', error);
    throw error;
  }
};
```

### Using in React Components

```javascript
import React, { useState, useEffect } from 'react';
import apiClient from '../services/apiClient';

const EmergencyList = () => {
  const [emergencies, setEmergencies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await apiClient.get('/api/emergencies');
        setEmergencies(response.data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <ul>
      {emergencies.map(emergency => (
        <li key={emergency.id}>{emergency.id}</li>
      ))}
    </ul>
  );
};
```

## How Token Refresh Works

1. When any API request receives a 401 Unauthorized response
2. The response interceptor automatically calls `authContext.refreshAccessToken()`
3. If refresh succeeds, the original request is retried with the new token
4. If refresh fails, the user is logged out and redirected to the login page

This happens transparently - your components don't need to handle token refresh logic.

## Error Handling

The API client will throw errors for:
- Network failures
- HTTP error status codes (4xx, 5xx)
- Timeout (after 10 seconds)

Always wrap API calls in try-catch blocks to handle errors appropriately.

## Configuration

The API client is configured with:
- **Base URL**: `http://localhost:8080` (API Gateway)
- **Timeout**: 10 seconds
- **Default Headers**: `Content-Type: application/json`

To modify these settings, edit `src/services/apiClient.js`.
