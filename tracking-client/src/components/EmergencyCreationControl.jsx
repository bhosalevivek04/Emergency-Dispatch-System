import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { emergencyApi } from '../services/api';
import './EmergencyCreationControl.css';

/**
 * EmergencyCreationControl component
 * Provides a floating button to activate emergency creation mode
 * Captures map clicks to set emergency location and displays form panel with priority selector
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
const EmergencyCreationControl = ({ onEmergencyCreated, onModeChange, onLocationHandlerReady, showToast }) => {
  const [isCreationMode, setIsCreationMode] = useState(false);
  const [selectedLocation, setSelectedLocation] = useState(null);
  const [priority, setPriority] = useState('MEDIUM');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState(null);

  /**
   * Toggle creation mode on/off
   */
  const toggleCreationMode = () => {
    const newMode = !isCreationMode;
    setIsCreationMode(newMode);
    setSelectedLocation(null);
    setError(null);
    if (onModeChange) {
      onModeChange(newMode);
    }
  };

  /**
   * Handle map click to set emergency location
   * This should be called from the parent component when the map is clicked
   */
  const handleLocationSelect = useCallback((lat, lng) => {
    if (isCreationMode) {
      setSelectedLocation({ lat, lng });
      setError(null);
    }
  }, [isCreationMode]);

  /**
   * Submit emergency creation request
   */
  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!selectedLocation) {
      setError('Please click on the map to select an emergency location');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const newEmergency = await emergencyApi.create({
        latitude: selectedLocation.lat,
        longitude: selectedLocation.lng,
        priority: priority,
      });

      // Show success toast notification
      if (showToast) {
        showToast(`Emergency ${newEmergency.id} created successfully`, 'success');
      }

      // Notify parent component of successful creation
      if (onEmergencyCreated) {
        onEmergencyCreated(newEmergency);
      }

      // Reset form
      setIsCreationMode(false);
      setSelectedLocation(null);
      setPriority('MEDIUM');
      if (onModeChange) {
        onModeChange(false);
      }
    } catch (err) {
      const errorMessage = err.response?.data?.message || 'Failed to create emergency. Please try again.';
      setError(errorMessage);
      
      // Show error toast notification
      if (showToast) {
        showToast(errorMessage, 'error');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * Cancel emergency creation
   */
  const handleCancel = () => {
    setIsCreationMode(false);
    setSelectedLocation(null);
    setError(null);
    setPriority('MEDIUM');
    if (onModeChange) {
      onModeChange(false);
    }
  };

  // Register map location handler with parent dashboard.
  useEffect(() => {
    if (onLocationHandlerReady) {
      onLocationHandlerReady(handleLocationSelect);
      return () => onLocationHandlerReady(null);
    }
    return undefined;
  }, [onLocationHandlerReady, handleLocationSelect]);

  return (
    <>
      {/* Floating button to activate creation mode */}
      {!isCreationMode && (
        <button
          className="emergency-creation-fab"
          onClick={toggleCreationMode}
          aria-label="Create new emergency"
          title="Create new emergency"
        >
          <span className="fab-icon">+</span>
        </button>
      )}

      {/* Form panel displayed when in creation mode */}
      {isCreationMode && (
        <div className="emergency-creation-panel">
          <div className="panel-header">
            <h3>Create Emergency</h3>
            <button
              className="close-button"
              onClick={handleCancel}
              aria-label="Close"
            >
              ×
            </button>
          </div>

          <form onSubmit={handleSubmit} className="emergency-form">
            {/* Location status */}
            <div className="form-section">
              <label className="form-label">Location</label>
              {selectedLocation ? (
                <div className="location-display">
                  <span className="location-icon">📍</span>
                  <span className="location-coords">
                    {selectedLocation.lat.toFixed(4)}, {selectedLocation.lng.toFixed(4)}
                  </span>
                </div>
              ) : (
                <div className="location-prompt">
                  <span className="prompt-icon">👆</span>
                  <span className="prompt-text">Click on the map to set location</span>
                </div>
              )}
            </div>

            {/* Priority selector */}
            <div className="form-section">
              <label className="form-label" htmlFor="priority-select">
                Priority
              </label>
              <div className="priority-selector">
                <button
                  type="button"
                  className={`priority-button priority-high ${priority === 'HIGH' ? 'selected' : ''}`}
                  onClick={() => setPriority('HIGH')}
                >
                  <span className="priority-badge">HIGH</span>
                </button>
                <button
                  type="button"
                  className={`priority-button priority-medium ${priority === 'MEDIUM' ? 'selected' : ''}`}
                  onClick={() => setPriority('MEDIUM')}
                >
                  <span className="priority-badge">MEDIUM</span>
                </button>
                <button
                  type="button"
                  className={`priority-button priority-low ${priority === 'LOW' ? 'selected' : ''}`}
                  onClick={() => setPriority('LOW')}
                >
                  <span className="priority-badge">LOW</span>
                </button>
              </div>
            </div>

            {/* Error message */}
            {error && (
              <div className="error-message" role="alert">
                {error}
              </div>
            )}

            {/* Action buttons */}
            <div className="form-actions">
              <button
                type="button"
                className="cancel-button"
                onClick={handleCancel}
                disabled={isSubmitting}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="submit-button"
                disabled={isSubmitting || !selectedLocation}
              >
                {isSubmitting ? 'Creating...' : 'Create Emergency'}
              </button>
            </div>
          </form>
        </div>
      )}
    </>
  );
};

EmergencyCreationControl.propTypes = {
  onEmergencyCreated: PropTypes.func.isRequired,
  onModeChange: PropTypes.func,
  onLocationHandlerReady: PropTypes.func,
  showToast: PropTypes.func,
};

export default EmergencyCreationControl;
