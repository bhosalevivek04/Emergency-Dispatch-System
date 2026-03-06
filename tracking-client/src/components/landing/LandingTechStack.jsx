const technologies = [
  { name: 'Java', icon: '☕', className: 'red' },
  { name: 'Spring Boot', icon: '🍃', className: 'green' },
  { name: 'Apache Kafka', icon: '⚡', className: 'dark' },
  { name: 'Redis', icon: '⚡', className: 'red' },
  { name: 'PostgreSQL', icon: '🐘', className: 'blue' },
  { name: 'React', icon: '⚛️', className: 'sky' },
  { name: 'Docker', icon: '🐳', className: 'sky' },
  { name: 'WebSocket', icon: '🔌', className: 'purple' },
];

function LandingTechStack() {
  return (
    <section id="technology" className="landing-section landing-section-muted">
      <div className="landing-container">
        <div className="landing-section-header">
          <h2>Technology Stack</h2>
          <p>Built with industry-standard technologies for enterprise-style performance.</p>
        </div>

        <div className="landing-card-grid landing-card-grid-4">
          {technologies.map((tech) => (
            <article key={tech.name} className="landing-tech-card">
              <div className={`landing-tech-icon ${tech.className}`}>{tech.icon}</div>
              <div className="landing-tech-title">{tech.name}</div>
            </article>
          ))}
        </div>

        <div className="landing-architecture-box">
          <h3>System Architecture</h3>
          <div className="landing-card-grid landing-card-grid-3">
            <article className="landing-architecture-card blue">
              <div className="landing-architecture-emoji">🎨</div>
              <h4>Frontend Layer</h4>
              <p>React, maps, citizen request flow, dashboard views, and WebSocket clients.</p>
            </article>
            <article className="landing-architecture-card green">
              <div className="landing-architecture-emoji">⚙️</div>
              <h4>Backend Services</h4>
              <p>Spring Boot microservices for auth, emergency intake, dispatch, ambulance, and tracking.</p>
            </article>
            <article className="landing-architecture-card purple">
              <div className="landing-architecture-emoji">💾</div>
              <h4>Data Layer</h4>
              <p>PostgreSQL, Redis, and Kafka to support persistence, state management, and events.</p>
            </article>
          </div>
        </div>
      </div>
    </section>
  );
}

export default LandingTechStack;
