import { useEffect } from 'react';
import LandingNavigation from '../components/landing/LandingNavigation';
import LandingHero from '../components/landing/LandingHero';
import LandingProblemSection from '../components/landing/LandingProblemSection';
import LandingSolutionSection from '../components/landing/LandingSolutionSection';
import LandingHowItWorks from '../components/landing/LandingHowItWorks';
import LandingFeaturesSection from '../components/landing/LandingFeaturesSection';
import LandingEmergencyCta from '../components/landing/LandingEmergencyCta';
import LandingTrackSection from '../components/landing/LandingTrackSection';
import LandingTechStack from '../components/landing/LandingTechStack';
import LandingDemoSection from '../components/landing/LandingDemoSection';
import LandingFooter from '../components/landing/LandingFooter';
import './LandingPage.css';

function LandingPage() {
  useEffect(() => {
    document.title = 'Emergency Dispatch System — Real-time Ambulance Coordination';
  }, []);

  return (
    <div className="landing-page">
      <LandingNavigation />
      <LandingHero />
      <LandingProblemSection />
      <LandingSolutionSection />
      <LandingHowItWorks />
      <LandingFeaturesSection />
      <LandingEmergencyCta />
      <LandingTrackSection />
      <LandingTechStack />
      <LandingDemoSection />
      <LandingFooter />
    </div>
  );
}

export default LandingPage;
