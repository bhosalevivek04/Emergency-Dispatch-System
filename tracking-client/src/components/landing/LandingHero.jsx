import { Link } from 'react-router-dom';
import { Play } from 'lucide-react';

function LandingHero() {
  return (
    <section id="home" className="landing-hero-section">
      <div className="landing-hero-backdrop">
        <div className="landing-hero-overlay" />
      </div>

      <div className="landing-container landing-hero-grid">
        <div className="landing-hero-copy">
          <h1>Emergency Dispatch System</h1>
          <p>
            A real-time ambulance coordination platform that automatically dispatches the nearest
            ambulance and enables live tracking during emergencies.
          </p>

          <div className="landing-hero-actions">
            <Link to="/citizen" className="landing-btn landing-btn-primary landing-btn-lg">
              Request Emergency
            </Link>
            <Link to="/login" className="landing-btn landing-btn-secondary landing-btn-lg">
              <Play className="landing-icon-sm" />
              Staff Login
            </Link>
          </div>

          <div className="landing-hero-stats">
            <div>
              <strong>24/7</strong>
              <span>Available</span>
            </div>
            <div>
              <strong>{'<10s'}</strong>
              <span>Auto-Dispatch</span>
            </div>
            <div>
              <strong>Real-time</strong>
              <span>Tracking</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export default LandingHero;

