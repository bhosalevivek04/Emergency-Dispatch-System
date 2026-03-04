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
          text: 'Connected',
          icon: '●',
        };
      case 'connecting':
        return {
          color: '#ff9800',
          text: 'Connecting...',
          icon: '◐',
        };
      case 'disconnected':
        return {
          color: '#f44336',
          text: 'Disconnected',
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
        padding: '4px 12px',
        backgroundColor: 'rgba(255, 255, 255, 0.1)',
        borderRadius: '4px',
        fontSize: '14px',
        fontWeight: '500',
      }}
      title={`WebSocket status: ${config.text}`}
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
      <span style={{ color: 'white' }}>
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
  status: PropTypes.oneOf(['connected', 'connecting', 'disconnected']).isRequired,
};

export default ConnectionStatus;
