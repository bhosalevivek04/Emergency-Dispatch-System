import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, Menu, X } from 'lucide-react';

function scrollToSection(id, onDone) {
  const element = document.getElementById(id);
  if (element) {
    element.scrollIntoView({ behavior: 'smooth' });
  }
  if (onDone) {
    onDone();
  }
}

function LandingNavigation() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <nav className="landing-nav">
      <div className="landing-container">
        <div className="landing-nav-bar">
          <Link to="/" className="landing-brand">
            <div className="landing-brand-icon">
              <AlertCircle className="landing-icon-md" />
            </div>
            <span>Emergency Dispatch</span>
          </Link>

          <div className="landing-nav-links desktop-nav">
            <button type="button" onClick={() => scrollToSection('home')}>Home</button>
            <button type="button" onClick={() => scrollToSection('how-it-works')}>How It Works</button>
            <button type="button" onClick={() => scrollToSection('features')}>Features</button>
            <button type="button" onClick={() => scrollToSection('technology')}>Technology</button>
            <Link to="/citizen" className="landing-btn landing-btn-primary">Request Emergency</Link>
            <Link to="/login" className="landing-nav-login">Login</Link>
          </div>

          <button
            type="button"
            className="landing-mobile-toggle mobile-nav"
            aria-label="Toggle menu"
            onClick={() => setIsMenuOpen((current) => !current)}
          >
            {isMenuOpen ? <X className="landing-icon-md" /> : <Menu className="landing-icon-md" />}
          </button>
        </div>

        {isMenuOpen && (
          <div className="landing-mobile-panel mobile-nav">
            <button type="button" onClick={() => scrollToSection('home', () => setIsMenuOpen(false))}>Home</button>
            <button type="button" onClick={() => scrollToSection('features', () => setIsMenuOpen(false))}>Features</button>
            <button type="button" onClick={() => scrollToSection('how-it-works', () => setIsMenuOpen(false))}>How It Works</button>
            <button type="button" onClick={() => scrollToSection('technology', () => setIsMenuOpen(false))}>Technology</button>
            <Link to="/citizen" onClick={() => setIsMenuOpen(false)}>Request Emergency</Link>
            <Link to="/login" onClick={() => setIsMenuOpen(false)}>Login</Link>
          </div>
        )}
      </div>
    </nav>
  );
}

export default LandingNavigation;
