import { Zap, Navigation, MapPin, ListOrdered, Wifi } from 'lucide-react';

const solutions = [
  {
    icon: Zap,
    title: 'Instant Emergency Creation',
    description: 'One-click emergency request system with automatic location detection.',
  },
  {
    icon: Navigation,
    title: 'Automatic Assignment',
    description: 'System automatically assigns the nearest available ambulance using dispatch logic and route estimates.',
  },
  {
    icon: MapPin,
    title: 'Live Ambulance Tracking',
    description: 'Real-time GPS tracking on an interactive map with ETA updates.',
  },
  {
    icon: ListOrdered,
    title: 'Priority-based Handling',
    description: 'Intelligent priority queue management for critical emergencies.',
  },
  {
    icon: Wifi,
    title: 'Real-time Updates',
    description: 'WebSocket-powered instant notifications and status updates.',
  },
];

function LandingSolutionSection() {
  return (
    <section className="landing-section">
      <div className="landing-container">
        <div className="landing-section-header">
          <h2>Our Solution</h2>
          <p>
            The Emergency Dispatch System uses modern services and live tracking to provide
            immediate, visible ambulance coordination.
          </p>
        </div>

        <div className="landing-card-grid landing-card-grid-3">
          {solutions.map(({ icon: Icon, title, description }) => (
            <article key={title} className="landing-solution-card">
              <div className="landing-card-icon solid-red">
                <Icon className="landing-icon-md white" />
              </div>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

export default LandingSolutionSection;
