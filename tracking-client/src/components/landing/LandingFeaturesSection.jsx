import { MapPin, Zap, Database, Wifi, Network, TrendingUp } from 'lucide-react';

const features = [
  {
    icon: MapPin,
    title: 'Real-time GPS Tracking',
    description: 'Precise ambulance location tracking with live map updates.',
  },
  {
    icon: Zap,
    title: 'Kafka Event Architecture',
    description: 'Event-driven system for scalable and reliable message processing.',
  },
  {
    icon: Database,
    title: 'Redis Dispatch State',
    description: 'Fast state checks for availability, locks, queue management, and idempotency.',
  },
  {
    icon: Wifi,
    title: 'WebSocket Real-time Updates',
    description: 'Instant communication for live status and location updates.',
  },
  {
    icon: Network,
    title: 'Microservices Architecture',
    description: 'Separate services for dispatching, tracking, emergency intake, auth, and notifications.',
  },
  {
    icon: TrendingUp,
    title: 'Scalable Backend',
    description: 'Designed to handle concurrent emergencies with durable messaging and persistence.',
  },
];

function LandingFeaturesSection() {
  return (
    <section id="features" className="landing-section">
      <div className="landing-container">
        <div className="landing-section-header">
          <h2>Key Features</h2>
          <p>Built with modern technologies for reliability, speed, and operational visibility.</p>
        </div>

        <div className="landing-card-grid landing-card-grid-3">
          {features.map(({ icon: Icon, title, description }) => (
            <article key={title} className="landing-feature-card">
              <div className="landing-card-icon gradient">
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

export default LandingFeaturesSection;
