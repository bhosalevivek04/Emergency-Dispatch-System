import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import './ToastNotification.css';

/**
 * ToastNotification component for displaying temporary messages
 * Supports success, error, info, and warning types with auto-dismiss and stacking
 */
const ToastNotification = ({ message, type = 'info', duration = 5000, onDismiss }) => {
  const [isVisible, setIsVisible] = useState(true);
  const [isExiting, setIsExiting] = useState(false);

  useEffect(() => {
    // Auto-dismiss for success and info types
    if ((type === 'success' || type === 'info') && duration > 0) {
      const timer = setTimeout(() => {
        handleDismiss();
      }, duration);

      return () => clearTimeout(timer);
    }
  }, [type, duration]);

  const handleDismiss = () => {
    setIsExiting(true);
    setTimeout(() => {
      setIsVisible(false);
      if (onDismiss) {
        onDismiss();
      }
    }, 300); // Match animation duration
  };

  if (!isVisible) {
    return null;
  }

  const typeStyles = {
    success: { backgroundColor: '#10b981', color: '#ffffff' },
    error: { backgroundColor: '#ef4444', color: '#ffffff' },
    info: { backgroundColor: '#3b82f6', color: '#ffffff' },
    warning: { backgroundColor: '#f59e0b', color: '#1f2937' },
  };

  const icons = {
    success: '✓',
    error: '✕',
    info: 'ℹ',
    warning: '⚠',
  };

  return (
    <div
      className={`toast-notification ${isExiting ? 'toast-exit' : 'toast-enter'}`}
      style={typeStyles[type]}
      role="alert"
    >
      <div className="toast-content">
        <span className="toast-icon">{icons[type]}</span>
        <span className="toast-message">{message}</span>
      </div>
      <button
        onClick={handleDismiss}
        className="toast-dismiss"
        aria-label="Dismiss notification"
      >
        ×
      </button>
    </div>
  );
};

ToastNotification.propTypes = {
  message: PropTypes.string.isRequired,
  type: PropTypes.oneOf(['success', 'error', 'info', 'warning']),
  duration: PropTypes.number,
  onDismiss: PropTypes.func,
};

export default ToastNotification;
