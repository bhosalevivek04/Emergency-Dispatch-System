import React from 'react';
import PropTypes from 'prop-types';

/**
 * ConnectionStatus component displays the WebSocket connection status.
 * Shows connected, connecting, or disconnected states with appropriate colors.
 */
const ConnectionStatus = ({ status }) => {
  const getStatusConfig = () => {
    switch (status) {
      case 'connected':
        return {
          color: '#4caf50',
          text: 'Live',
          icon: '●',
        };
      case 'connecting':
        return {
          color: '#ff9800',
          text: 'Reconnecting',
          icon: '◐',
        };
      case 'error':
        return {
          color: '#f44336',
          text: 'Offline',
          icon: '○',
        };
      case 'disconnected':
        return {
          color: '#f44336',
          text: 'Offline',
          icon: '○',
        };
      default:
        return {
          color: '#9e9e9e',
          text: 'Unknown',
          icon: '?',
        };
    }
  };

  const config = getStatusConfig();

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '6px',
        padding: '4px 10px',
        backgroundColor: '#f3f4f6',
        borderRadius: '4px',
        border: '1px solid #d1d5db',
        fontSize: '13px',
        fontWeight: '500',
      }}
      title={`WebSocket status: ${config.text}`}
      role="status"
      aria-live="polite"
    >
      <span
        style={{
          color: config.color,
          fontSize: '16px',
          lineHeight: '1',
          animation: status === 'connecting' ? 'pulse 1.5s ease-in-out infinite' : 'none',
        }}
      >
        {config.icon}
      </span>
      <span style={{ color: '#111827' }}>
        {config.text}
      </span>
      <style>
        {`
          @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.4; }
          }
        `}
      </style>
    </div>
  );
};

ConnectionStatus.propTypes = {
  status: PropTypes.oneOf(['connected', 'connecting', 'disconnected', 'error']).isRequired,
};

export default ConnectionStatus;
