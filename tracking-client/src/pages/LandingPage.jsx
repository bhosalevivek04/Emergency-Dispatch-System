import LandingNavigation from '../components/landing/LandingNavigation';
import LandingHero from '../components/landing/LandingHero';
import LandingProblemSection from '../components/landing/LandingProblemSection';
import LandingSolutionSection from '../components/landing/LandingSolutionSection';
import LandingHowItWorks from '../components/landing/LandingHowItWorks';
import LandingFeaturesSection from '../components/landing/LandingFeaturesSection';
import LandingEmergencyCta from '../components/landing/LandingEmergencyCta';
import LandingTechStack from '../components/landing/LandingTechStack';
import LandingDemoSection from '../components/landing/LandingDemoSection';
import LandingFooter from '../components/landing/LandingFooter';
import './LandingPage.css';

function LandingPage() {
  return (
    <div className="landing-page">
      <LandingNavigation />
      <LandingHero />
      <LandingProblemSection />
      <LandingSolutionSection />
      <LandingHowItWorks />
      <LandingFeaturesSection />
      <LandingEmergencyCta />
      <LandingTechStack />
      <LandingDemoSection />
      <LandingFooter />
    </div>
  );
}

export default LandingPage;
