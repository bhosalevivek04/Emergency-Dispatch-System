import { AlertTriangle, Clock, MapPin, Eye } from 'lucide-react';

const problems = [
  {
    icon: AlertTriangle,
    title: 'Manual Dispatch',
    description: 'Ambulance dispatch is often done manually, leading to delays and inefficiencies in critical situations.',
  },
  {
    icon: Eye,
    title: 'No Real-time Tracking',
    description: 'Patients and families have no visibility into ambulance location or estimated arrival time.',
  },
  {
    icon: Clock,
    title: 'Delayed Response',
    description: 'Finding and dispatching the nearest ambulance takes precious time during emergencies.',
  },
  {
    icon: MapPin,
    title: 'Lack of Transparency',
    description: 'No clear communication between dispatch centers, ambulances, and emergency requesters.',
  },
];

function LandingProblemSection() {
  return (
    <section className="landing-section landing-section-muted">
      <div className="landing-container">
        <div className="landing-section-header">
          <h2>The Problem</h2>
          <p>Current emergency dispatch systems face critical challenges that can cost lives.</p>
          <div className="landing-alert-box">
            <p>
              <span>Critical Fact:</span> Every minute without medical response during cardiac arrest
              reduces survival chances.
            </p>
          </div>
        </div>

        <div className="landing-card-grid landing-card-grid-4">
          {problems.map(({ icon: Icon, title, description }) => (
            <article key={title} className="landing-info-card">
              <div className="landing-card-icon red">
                <Icon className="landing-icon-md" />
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

export default LandingProblemSection;
