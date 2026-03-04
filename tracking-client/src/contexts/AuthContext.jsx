import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { initializeApiClient } from '../services/apiClient';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};

export const AuthProvider = ({ children }) => {
  const navigate = useNavigate();

  const [authState, setAuthState] = useState({
    isAuthenticated: false,
    user: null,
    accessToken: null,
    refreshToken: null,
  });

  const [isRestoringSession, setIsRestoringSession] = useState(true);

  // Token refresh
  const refreshAccessToken = useCallback(async () => {
    try {
      const refreshToken = authState.refreshToken || sessionStorage.getItem('refreshToken');
      if (!refreshToken) throw new Error('No refresh token available');

      const response = await fetch('http://localhost:8080/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) throw new Error('Token refresh failed');

      const data = await response.json();
      setAuthState((prev) => ({ ...prev, accessToken: data.accessToken }));
      return data.accessToken;
    } catch (error) {
      console.error('Token refresh error:', error);
      sessionStorage.removeItem('refreshToken');
      sessionStorage.removeItem('user');
      setAuthState({ isAuthenticated: false, user: null, accessToken: null, refreshToken: null });
      navigate('/login');
      throw error;
    }
  }, [authState.refreshToken, navigate]);

  // Initialize apiClient with live token getter
  useEffect(() => {
    initializeApiClient({
      get accessToken() {
        return authState.accessToken;
      },
      refreshAccessToken,
    });
  }, [authState.accessToken, refreshAccessToken]);

  // Session restoration on app load
  useEffect(() => {
    const restoreSession = async () => {
      try {
        const storedRefreshToken = sessionStorage.getItem('refreshToken');
        const storedUser = sessionStorage.getItem('user');
        if (!storedRefreshToken || !storedUser) return;

        const response = await fetch('http://localhost:8080/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: storedRefreshToken }),
        });

        if (!response.ok) throw new Error('Session restore failed');

        const data = await response.json();
        const user = JSON.parse(storedUser);

        setAuthState({
          isAuthenticated: true,
          user,
          accessToken: data.accessToken,
          refreshToken: storedRefreshToken,
        });
      } catch (error) {
        console.error('Session restoration failed:', error);
        sessionStorage.removeItem('refreshToken');
        sessionStorage.removeItem('user');
      } finally {
        setIsRestoringSession(false);
      }
    };

    restoreSession();
  }, []);

  // Login
  const login = useCallback(async (username, password) => {
    const response = await fetch('http://localhost:8080/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || 'Login failed');
    }

    const data = await response.json();

    const user = {
      username: data.username,
      roles: data.roles,
      ambulanceId: data.ambulanceId || null,
    };

    sessionStorage.setItem('refreshToken', data.refreshToken);
    sessionStorage.setItem('user', JSON.stringify(user));

    setAuthState({
      isAuthenticated: true,
      user,
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
    });

    if (data.roles?.includes('ADMIN')) {
      navigate('/dashboard/admin');
    } else if (data.roles?.includes('DISPATCHER')) {
      navigate('/dashboard/dispatcher');
    } else if (data.roles?.includes('AMBULANCE_DRIVER')) {
      navigate('/dashboard/driver');
    }
  }, [navigate]);

  // Logout
  const logout = useCallback(async () => {
    try {
      const rt = authState.refreshToken || sessionStorage.getItem('refreshToken');
      await fetch('http://localhost:8080/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authState.accessToken}`,
        },
        body: JSON.stringify({ refreshToken: rt }),
      });
    } catch (error) {
      console.error('Logout API error:', error);
    } finally {
      sessionStorage.removeItem('refreshToken');
      sessionStorage.removeItem('user');
      setAuthState({ isAuthenticated: false, user: null, accessToken: null, refreshToken: null });
      navigate('/login');
    }
  }, [authState.accessToken, authState.refreshToken, navigate]);

  const value = { ...authState, login, logout, refreshAccessToken };

  if (isRestoringSession) {
    return (
      <AuthContext.Provider value={value}>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', fontSize: 18, color: '#666' }}>
          Restoring session...
        </div>
      </AuthContext.Provider>
    );
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
