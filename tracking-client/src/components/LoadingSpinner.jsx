import PropTypes from 'prop-types';
import './LoadingSpinner.css';

/**
 * LoadingSpinner component for displaying loading states
 * Supports small, medium, and large sizes with optional overlay mode
 */
const LoadingSpinner = ({ size = 'medium', overlay = false }) => {
  const sizeClasses = {
    small: 'spinner-small',
    medium: 'spinner-medium',
    large: 'spinner-large',
  };

  const spinner = (
    <div
      className={`spinner ${sizeClasses[size]}`}
      role="status"
      aria-label="Loading"
    />
  );

  if (overlay) {
    return (
      <div className="spinner-overlay">
        <div className="spinner-overlay-content">
          {spinner}
          <span className="spinner-text">Loading...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="spinner-container">
      {spinner}
    </div>
  );
};

LoadingSpinner.propTypes = {
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  overlay: PropTypes.bool,
};

export default LoadingSpinner;
