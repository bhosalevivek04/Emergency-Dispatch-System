import { Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import { useAuth } from './contexts/AuthContext';

const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const CitizenRequestPage = lazy(() => import('./pages/CitizenRequestPage'));
const LandingPage = lazy(() => import('./pages/LandingPage'));
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'));
const DispatcherDashboard = lazy(() => import('./pages/DispatcherDashboard'));
const DriverDashboard = lazy(() => import('./pages/DriverDashboard'));

function App() {
  const { isAuthenticated, user } = useAuth();

  // Helper function to get default dashboard route based on user role
  const getDefaultDashboard = () => {
    if (!isAuthenticated || !user) {
      return '/';
    }
    
    if (user.roles?.includes('ADMIN')) {
      return '/dashboard/admin';
    } else if (user.roles?.includes('DISPATCHER')) {
      return '/dashboard/dispatcher';
    } else if (user.roles?.includes('AMBULANCE_DRIVER')) {
      return '/dashboard/driver';
    }
    
    return '/login';
  };

  return (
    <Suspense fallback={<div style={{ padding: '1rem' }}>Loading...</div>}>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        {/* Login route */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/citizen" element={<CitizenRequestPage />} />

      {/* Admin dashboard - requires ADMIN role */}
        <Route
          path="/dashboard/admin"
          element={
            <ProtectedRoute requiredRoles={['ADMIN']}>
              <AdminDashboard />
            </ProtectedRoute>
          }
        />

      {/* Dispatcher dashboard - requires DISPATCHER role */}
        <Route
          path="/dashboard/dispatcher"
          element={
            <ProtectedRoute requiredRoles={['DISPATCHER']}>
              <DispatcherDashboard />
            </ProtectedRoute>
          }
        />

      {/* Driver dashboard - requires AMBULANCE_DRIVER role */}
        <Route
          path="/dashboard/driver"
          element={
            <ProtectedRoute requiredRoles={['AMBULANCE_DRIVER']}>
              <DriverDashboard />
            </ProtectedRoute>
          }
        />

      {/* Catch-all route - redirect to appropriate dashboard or landing */}
        <Route path="*" element={<Navigate to={getDefaultDashboard()} replace />} />
      </Routes>
    </Suspense>
  );
}

export default App;
