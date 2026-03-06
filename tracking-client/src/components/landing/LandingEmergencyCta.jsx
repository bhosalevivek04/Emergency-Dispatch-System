import { Link } from 'react-router-dom';
import { AlertCircle, MapPin, Phone } from 'lucide-react';

function LandingEmergencyCta() {
  return (
    <section className="landing-cta-section">
      <div className="landing-container">
        <div className="landing-cta-header">
          <AlertCircle className="landing-cta-icon" />
          <h2>Need Help Right Now?</h2>
          <p>Every second counts in an emergency. Get immediate assistance with one click.</p>
        </div>

        <div className="landing-card-grid landing-card-grid-3">
          <Link to="/citizen" className="landing-cta-card primary">
            <AlertCircle className="landing-icon-lg" />
            <div className="landing-cta-card-title">Request Emergency</div>
            <div className="landing-cta-card-copy">Get immediate ambulance dispatch</div>
          </Link>

          <Link to="/citizen" className="landing-cta-card secondary">
            <MapPin className="landing-icon-lg" />
            <div className="landing-cta-card-title">Track Existing Request</div>
            <div className="landing-cta-card-copy">Monitor your ambulance in real time</div>
          </Link>

          <a href="tel:911" className="landing-cta-card secondary">
            <Phone className="landing-icon-lg" />
            <div className="landing-cta-card-title">Contact Support</div>
            <div className="landing-cta-card-copy">Use your emergency number for life-threatening situations</div>
          </a>
        </div>

        <div className="landing-cta-note">
          For life-threatening emergencies, always call your local emergency number first.
        </div>
      </div>
    </section>
  );
}

export default LandingEmergencyCta;
