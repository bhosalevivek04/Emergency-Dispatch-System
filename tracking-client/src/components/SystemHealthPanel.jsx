import { useState, useEffect, useRef } from 'react';
import { diagnosticApi } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import LoadingSpinner from './LoadingSpinner';
import './SystemHealthPanel.css';

/**
 * SystemHealthPanel component
 * Monitors health status of all backend services
 * Polls health endpoints every 30 seconds
 * Admin-only component
 */
const SystemHealthPanel = () => {
  const [services, setServices] = useState([
    { name: 'Emergency Service', status: 'UNKNOWN' },
    { name: 'Dispatch Service', status: 'UNKNOWN' },
    { name: 'Ambulance Service', status: 'UNKNOWN' },
    { name: 'Tracking Service', status: 'UNKNOWN' },
    { name: 'Notification Service', status: 'UNKNOWN' },
    { name: 'Auth Service', status: 'UNKNOWN' },
  ]);
  const [lastChecked, setLastChecked] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const { showToast } = useToast();
  const intervalRef = useRef(null);

  /**
   * Check health status of all services
   */
  const checkServiceHealth = async () => {
    try {
      // Call the health endpoint (API Gateway aggregates all service health)
      const healthData = await diagnosticApi.getServiceHealth();
      
      // Update service statuses based on response
      const updatedServices = services.map(service => {
        // The health endpoint returns overall status
        // In a real implementation, you'd parse the response to get individual service statuses
        // For now, we'll use the overall status
        return {
          ...service,
          status: healthData.status || 'UNKNOWN'
        };
      });

      setServices(updatedServices);
      setLastChecked(new Date());
      setIsLoading(false);
    } catch (error) {
      console.error('Failed to check service health:', error);
      
      // Mark all services as DOWN on error
      const updatedServices = services.map(service => ({
        ...service,
        status: 'DOWN'
      }));
      
      setServices(updatedServices);
      setLastChecked(new Date());
      setIsLoading(false);
      
      // Only show toast on first error, not on every poll
      if (isLoading) {
        showToast('Failed to check service health', 'error');
      }
    }
  };

  // Initial health check and set up polling
  useEffect(() => {
    checkServiceHealth();

    // Poll every 30 seconds
    intervalRef.current = setInterval(() => {
      checkServiceHealth();
    }, 30000);

    // Cleanup interval on unmount
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  /**
   * Get status indicator class based on status
   */
  const getStatusClass = (status) => {
    switch (status) {
      case 'UP':
        return 'status-up';
      case 'DOWN':
        return 'status-down';
      default:
        return 'status-unknown';
    }
  };

  /**
   * Format last checked timestamp
   */
  const formatLastChecked = () => {
    if (!lastChecked) return 'Never';
    
    const now = new Date();
    const diffSeconds = Math.floor((now - lastChecked) / 1000);
    
    if (diffSeconds < 60) {
      return `${diffSeconds} seconds ago`;
    } else if (diffSeconds < 3600) {
      const minutes = Math.floor(diffSeconds / 60);
      return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
    } else {
      return lastChecked.toLocaleTimeString();
    }
  };

  if (isLoading) {
    return (
      <div className="system-health-panel">
        <h2>System Health</h2>
        <LoadingSpinner size="medium" />
      </div>
    );
  }

  return (
    <div className="system-health-panel">
      <div className="panel-header">
        <h2>System Health</h2>
        <div className="last-checked">
          Last checked: {formatLastChecked()}
        </div>
      </div>

      <div className="services-grid">
        {services.map((service) => (
          <div key={service.name} className="service-card">
            <div className="service-info">
              <span className="service-name">{service.name}</span>
              <div className={`status-indicator ${getStatusClass(service.status)}`}>
                <span className="status-dot"></span>
                <span className="status-text">{service.status}</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="health-legend">
        <div className="legend-item">
          <span className="status-dot status-up"></span>
          <span>Healthy</span>
        </div>
        <div className="legend-item">
          <span className="status-dot status-down"></span>
          <span>Unhealthy</span>
        </div>
        <div className="legend-item">
          <span className="status-dot status-unknown"></span>
          <span>Unknown</span>
        </div>
      </div>
    </div>
  );
};

export default SystemHealthPanel;
