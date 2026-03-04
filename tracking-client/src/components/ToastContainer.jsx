import { useState, useCallback } from 'react';
import PropTypes from 'prop-types';
import ToastNotification from './ToastNotification';
import './ToastContainer.css';

/**
 * ToastContainer component for managing and stacking multiple toast notifications
 */
const ToastContainer = () => {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info', duration = 5000) => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, type, duration }]);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  return (
    <div className="toast-container">
      {toasts.map((toast) => (
        <ToastNotification
          key={toast.id}
          message={toast.message}
          type={toast.type}
          duration={toast.duration}
          onDismiss={() => removeToast(toast.id)}
        />
      ))}
    </div>
  );
};

// Export a hook to use the toast functionality
export const useToast = () => {
  // This will be implemented with context in a real application
  // For now, return a simple interface
  return {
    showToast: (message, type = 'info', duration = 5000) => {
      console.log('Toast:', message, type);
      // In production, this would dispatch to a ToastContext
    },
  };
};

ToastContainer.propTypes = {};

export default ToastContainer;
