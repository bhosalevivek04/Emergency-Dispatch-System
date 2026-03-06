import { User, Server, Ambulance, Navigation2, MapPin } from 'lucide-react';

const steps = [
  {
    icon: User,
    title: 'Citizen Requests Emergency',
    description: 'A user opens the public flow and submits an emergency request with their location.',
  },
  {
    icon: Server,
    title: 'System Processes Request',
    description: 'The backend receives the request, queues it, and evaluates available ambulances.',
  },
  {
    icon: Ambulance,
    title: 'Nearest Ambulance Assigned',
    description: 'Dispatch publishes an assignment for the closest available ambulance.',
  },
  {
    icon: Navigation2,
    title: 'Ambulance Travels',
    description: 'The driver receives the assignment and begins the trip toward the emergency.',
  },
  {
    icon: MapPin,
    title: 'Live Tracking Enabled',
    description: 'Citizen and dispatcher can follow the ambulance in real time with status updates.',
  },
];

function LandingHowItWorks() {
  return (
    <section id="how-it-works" className="landing-section landing-section-muted">
      <div className="landing-container">
        <div className="landing-section-header">
          <h2>How It Works</h2>
          <p>A clear 5-step flow from emergency request to ambulance arrival.</p>
        </div>

        <div className="landing-timeline">
          <div className="landing-timeline-line" />
          <div className="landing-card-grid landing-card-grid-5">
            {steps.map(({ icon: Icon, title, description }, index) => (
              <article key={title} className="landing-step-card">
                <div className="landing-step-number">{index + 1}</div>
                <div className="landing-step-icon">
                  <Icon className="landing-icon-lg red" />
                </div>
                <h3>{title}</h3>
                <p>{description}</p>
              </article>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export default LandingHowItWorks;
