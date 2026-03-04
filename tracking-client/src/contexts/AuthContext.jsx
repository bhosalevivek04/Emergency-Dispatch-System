import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { initializeApiClient } from '../services/apiClient';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
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

  const refreshAccessToken = useCallback(async () => {
    try {
      const refreshToken = authState.refreshToken || sessionStorage.getItem('refreshToken');
      
      if (!refreshToken) {
        throw new Error('No refresh token available');
      }

      const response = await fetch('http://localhost:8080/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) {
        throw new Error('Token refresh failed');
      }

      const data = await response.json();
      
      setAuthState(prev => ({
        ...prev,
        accessToken: data.accessToken,
      }));

      return data.accessToken;
    } catch (error) {
      console.error('Token refresh error:', error);
      // Clear auth state and redirect to login on refresh failure
      sessionStorage.removeItem('refreshToken');
      sessionStorage.removeItem('user');
      setAuthState({
        isAuthenticated: false,
        user: null,
        accessToken: null,
        refreshToken: null,
      });
      navigate('/login');
      throw error;
    }
  }, [authState.refreshToken, navigate]);

  // Initialize API client with auth context reference
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

        if (!storedRefreshToken || !storedUser) {
          // No session to restore
          setIsRestoringSession(false);
          return;
        }

        // Attempt to refresh the access token
        const response = await fetch('http://localhost:8080/auth/refresh', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ refreshToken: storedRefreshToken }),
        });

        if (!response.ok) {
          throw new Error('Token refresh failed');
        }

        const data = await response.json();
        const user = JSON.parse(storedUser);

        // Restore the session
        setAuthState({
          isAuthenticated: true,
          user,
          accessToken: data.accessToken,
          refreshToken: storedRefreshToken,
        });

        console.log('Session restored successfully');
      } catch (error) {
        console.error('Session restoration failed:', error);
        // Clear invalid session data
        sessionStorage.removeItem('refreshToken');
        sessionStorage.removeItem('user');
      } finally {
        setIsRestoringSession(false);
      }
    };

    restoreSession();
  }, []); // Run only once on mount

  const login = useCallback(async (username, password) => {
    try {
      const response = await fetch('http://localhost:8080/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.message || 'Login failed');
      }

      const data = await response.json();
      
      // Store refresh token and user info in sessionStorage for session restoration
      sessionStorage.setItem('refreshToken', data.refreshToken);
      sessionStorage.setItem('user', JSON.stringify({
        username: data.username,
        roles: data.roles,
      }));
      
      setAuthState({
        isAuthenticated: true,
        user: {
          username: data.username,
          roles: data.roles,
        },
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
      });

      // Navigate to role-appropriate dashboard
      if (data.roles.includes('ADMIN')) {
        navigate('/dashboard/admin');
      } else if (data.roles.includes('DISPATCHER')) {
        navigate('/dashboard/dispatcher');
      } else if (data.roles.includes('AMBULANCE_DRIVER')) {
        navigate('/dashboard/driver');
      }
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  }, [navigate]);

  const logout = useCallback(async () => {
    try {
      // Attempt to call logout endpoint, but proceed regardless of result
      await fetch('http://localhost:8080/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authState.accessToken}`,
        },
      });
    } catch (error) {
      console.error('Logout API error:', error);
    } finally {
      // Clear sessionStorage
      sessionStorage.removeItem('refreshToken');
      sessionStorage.removeItem('user');
      
      // Clear authentication state regardless of API response
      setAuthState({
        isAuthenticated: false,
        user: null,
        accessToken: null,
        refreshToken: null,
      });
      navigate('/login');
    }
  }, [authState.accessToken, navigate]);

  const value = {
    ...authState,
    login,
    logout,
    refreshAccessToken,
  };

  // Show loading state while restoring session
  if (isRestoringSession) {
    return (
      <AuthContext.Provider value={value}>
        <div style={{ 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center', 
          height: '100vh',
          fontSize: '18px',
          color: '#666'
        }}>
          Restoring session...
        </div>
      </AuthContext.Provider>
    );
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
