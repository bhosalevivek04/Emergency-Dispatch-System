import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import AdminDashboard from './pages/AdminDashboard';
import DispatcherDashboard from './pages/DispatcherDashboard';
import DriverDashboard from './pages/DriverDashboard';
import ProtectedRoute from './components/ProtectedRoute';
import { useAuth } from './contexts/AuthContext';

function App() {
  const { isAuthenticated, user } = useAuth();

  // Helper function to get default dashboard route based on user role
  const getDefaultDashboard = () => {
    if (!isAuthenticated || !user) {
      return '/login';
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
    <Routes>
      {/* Login route */}
      <Route path="/login" element={<LoginPage />} />

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

      {/* Default route - redirect to appropriate dashboard or login */}
      <Route path="/" element={<Navigate to={getDefaultDashboard()} replace />} />

      {/* Catch-all route - redirect to appropriate dashboard or login */}
      <Route path="*" element={<Navigate to={getDefaultDashboard()} replace />} />
    </Routes>
  );
}

export default App;
