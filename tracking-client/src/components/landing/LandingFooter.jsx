import { Link } from 'react-router-dom';
import { Github, Linkedin, Mail, AlertCircle } from 'lucide-react';

function LandingFooter() {
  return (
    <footer className="landing-footer">
      <div className="landing-container">
        <div className="landing-footer-grid">
          <div className="landing-footer-brand">
            <div className="landing-brand">
              <div className="landing-brand-icon">
                <AlertCircle className="landing-icon-md" />
              </div>
              <span>Emergency Dispatch System</span>
            </div>
            <p>
              A real-time ambulance coordination platform that automatically dispatches the nearest
              ambulance and enables live tracking during emergencies.
            </p>
            <div className="landing-footer-socials">
              <a href="https://github.com/bhosalevivek04" target="_blank" rel="noreferrer">
                <Github className="landing-icon-sm" />
              </a>
              <a href="https://www.linkedin.com/in/vivekbhosale04" target="_blank" rel="noreferrer">
                <Linkedin className="landing-icon-sm" />
              </a>
              <a href="mailto:bhosalevivek04@gmail.com">
                <Mail className="landing-icon-sm" />
              </a>
            </div>
          </div>

          <div>
            <h3>Quick Links</h3>
            <ul>
              <li><a href="#home">Home</a></li>
              <li><a href="#features">Features</a></li>
              <li><a href="#how-it-works">How It Works</a></li>
              <li><a href="#technology">Technology</a></li>
            </ul>
          </div>

          <div>
            <h3>Dashboards</h3>
            <ul>
              <li><Link to="/citizen">Citizen Interface</Link></li>
              <li><Link to="/dashboard/dispatcher">Dispatcher</Link></li>
              <li><Link to="/dashboard/driver">Driver</Link></li>
              <li><Link to="/dashboard/admin">Admin</Link></li>
            </ul>
          </div>
        </div>

        <div className="landing-footer-bottom">
          <p>© {new Date().getFullYear()} Emergency Dispatch System.</p>
          <div className="landing-footer-bottom-links">
            <a href="https://github.com/bhosalevivek04/Emergency-Dispatch-System" target="_blank" rel="noreferrer">
              View on GitHub
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}

export default LandingFooter;
