import { Link } from 'react-router-dom';
import { User, Radio, Truck, Settings } from 'lucide-react';

const dashboards = [
  {
    icon: User,
    title: 'Citizen Interface',
    description: 'Request emergencies and track ambulances in real time.',
    link: '/citizen',
    tone: 'blue',
  },
  {
    icon: Radio,
    title: 'Dispatcher Dashboard',
    description: 'Monitor emergencies and manage operational response.',
    link: '/dashboard/dispatcher',
    tone: 'green',
  },
  {
    icon: Truck,
    title: 'Driver Dashboard',
    description: 'View assigned emergencies and navigate to locations.',
    link: '/dashboard/driver',
    tone: 'orange',
  },
  {
    icon: Settings,
    title: 'Admin Dashboard',
    description: 'Manage configuration, users, and visibility across the system.',
    link: '/dashboard/admin',
    tone: 'purple',
  },
];

function LandingDemoSection() {
  return (
    <section className="landing-section">
      <div className="landing-container">
        <div className="landing-section-header">
          <h2>Explore the System</h2>
          <p>Experience the interfaces designed for each role in the emergency response workflow.</p>
        </div>

        <div className="landing-card-grid landing-card-grid-4">
          {dashboards.map(({ icon: Icon, title, description, link, tone }) => (
            <Link key={title} to={link} className={`landing-dashboard-card ${tone}`}>
              <div className="landing-dashboard-icon">
                <Icon className="landing-icon-lg white" />
              </div>
              <h3>{title}</h3>
              <p>{description}</p>
              <span>View Dashboard →</span>
            </Link>
          ))}
        </div>

        {/* <div className="landing-demo-video">
          <div className="landing-demo-video-overlay">
            <div className="landing-play-pill">
              <div className="landing-play-button">
                <div className="landing-play-triangle" />
              </div>
              <span>Live System Demo</span>
            </div>
          </div>
        </div> */}
      </div>
    </section>
  );
}

export default LandingDemoSection;
