import PropTypes from 'prop-types';
import './StatusTimeline.css';

const STAGES = ['CREATED', 'ASSIGNED', 'ON_ROUTE', 'COMPLETED'];

const normalizeStatus = (status) => {
  if (!status) return 'CREATED';
  const normalized = String(status).toUpperCase();
  if (normalized === 'PENDING') return 'CREATED';
  if (normalized === 'ARRIVED') return 'ON_ROUTE';
  return normalized;
};

const getStageState = (stage, currentStage) => {
  const currentIndex = STAGES.indexOf(currentStage);
  const stageIndex = STAGES.indexOf(stage);

  if (stageIndex < currentIndex) return 'done';
  if (stageIndex === currentIndex) return 'active';
  return 'pending';
};

const StatusTimeline = ({ status, compact = false }) => {
  const current = normalizeStatus(status);

  return (
    <div className={`status-timeline ${compact ? 'compact' : ''}`} role="list" aria-label="Emergency lifecycle">
      {STAGES.map((stage) => {
        const stageState = getStageState(stage, current);
        return (
          <div key={stage} className={`timeline-step ${stageState}`} role="listitem">
            <span className="timeline-dot" />
            <span className="timeline-label">{stage.replace('_', ' ')}</span>
          </div>
        );
      })}
    </div>
  );
};

StatusTimeline.propTypes = {
  status: PropTypes.string,
  compact: PropTypes.bool,
};

export default StatusTimeline;
