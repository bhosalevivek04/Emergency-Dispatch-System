import PropTypes from 'prop-types';
import './SkeletonLoader.css';

/**
 * SkeletonLoader component for displaying animated placeholder content
 * Supports text, card, table, and map types
 */
const SkeletonLoader = ({ type = 'text', count = 1 }) => {
  const renderText = () => (
    <div className="skeleton-text-container">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="skeleton skeleton-text-line" />
      ))}
    </div>
  );

  const renderCard = () => (
    <div className="skeleton-card-container">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="skeleton-card">
          <div className="skeleton skeleton-card-title" />
          <div className="skeleton skeleton-card-line" />
          <div className="skeleton skeleton-card-line-short" />
        </div>
      ))}
    </div>
  );

  const renderTable = () => (
    <div className="skeleton-table-container">
      {/* Table header */}
      <div className="skeleton-table-row">
        <div className="skeleton skeleton-table-header" />
        <div className="skeleton skeleton-table-header" />
        <div className="skeleton skeleton-table-header" />
      </div>
      {/* Table rows */}
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="skeleton-table-row">
          <div className="skeleton skeleton-table-cell" />
          <div className="skeleton skeleton-table-cell" />
          <div className="skeleton skeleton-table-cell" />
        </div>
      ))}
    </div>
  );

  const renderMap = () => (
    <div className="skeleton skeleton-map">
      {/* Simulate map tiles loading */}
      <div className="skeleton-map-grid">
        {Array.from({ length: 9 }).map((_, index) => (
          <div
            key={index}
            className="skeleton skeleton-map-tile"
            style={{ animationDelay: `${index * 0.1}s` }}
          />
        ))}
      </div>
    </div>
  );

  const renderByType = () => {
    switch (type) {
      case 'text':
        return renderText();
      case 'card':
        return renderCard();
      case 'table':
        return renderTable();
      case 'map':
        return renderMap();
      default:
        return renderText();
    }
  };

  return (
    <div className="skeleton-loader" role="status" aria-label="Loading content">
      {renderByType()}
    </div>
  );
};

SkeletonLoader.propTypes = {
  type: PropTypes.oneOf(['text', 'card', 'table', 'map']),
  count: PropTypes.number,
};

export default SkeletonLoader;
