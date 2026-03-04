import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import PropTypes from 'prop-types';

/**
 * ProtectedRoute component for role-based access control
 * Checks authentication status and user roles before rendering children
 * Redirects to /login if not authenticated
 * Redirects to authorized dashboard if role mismatch
 */
const ProtectedRoute = ({ children, requiredRoles }) => {
  const { isAuthenticated, user } = useAuth();

  // Redirect to login if not authenticated
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // If specific roles are required, check if user has at least one of them
  if (requiredRoles && requiredRoles.length > 0) {
    const hasRequiredRole = requiredRoles.some(role => 
      user?.roles?.includes(role)
    );

    if (!hasRequiredRole) {
      // Redirect to user's authorized dashboard based on their role
      if (user?.roles?.includes('ADMIN')) {
        return <Navigate to="/dashboard/admin" replace />;
      } else if (user?.roles?.includes('DISPATCHER')) {
        return <Navigate to="/dashboard/dispatcher" replace />;
      } else if (user?.roles?.includes('AMBULANCE_DRIVER')) {
        return <Navigate to="/dashboard/driver" replace />;
      }
      
      // If no recognized role, redirect to login
      return <Navigate to="/login" replace />;
    }
  }

  // User is authenticated and has required role (or no role check needed)
  return children;
};

ProtectedRoute.propTypes = {
  children: PropTypes.node.isRequired,
  requiredRoles: PropTypes.arrayOf(PropTypes.string),
};

ProtectedRoute.defaultProps = {
  requiredRoles: [],
};

export default ProtectedRoute;
