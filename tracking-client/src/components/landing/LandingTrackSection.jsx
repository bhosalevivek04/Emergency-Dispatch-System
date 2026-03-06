import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, MapPin, Clock, CheckCircle } from 'lucide-react';

const apiBase = 'http://localhost:8080';

function LandingTrackSection() {
    const [trackingId, setTrackingId] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleTrack = async (e) => {
        e.preventDefault();
        if (!trackingId.trim()) {
            setError('Please enter a request ID');
            return;
        }
        setIsLoading(true);
        setError('');

        try {
            const response = await fetch(`${apiBase}/api/emergencies/public/${trackingId.trim()}`, {
                headers: { 'Content-Type': 'application/json' },
            });
            if (!response.ok) {
                throw new Error('Request ID not found');
            }
            // Valid ID - navigate to citizen page with session state
            navigate(`/citizen?resume=${trackingId.trim()}`);
        } catch (err) {
            setError(err.message || 'Unable to find request. Please check your ID and try again.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <section className="landing-section">
            <div className="landing-container">
                <div className="landing-section-header">
                    <h2>Track Your Request</h2>
                    <p>Enter your request ID to track the status of your emergency in real time.</p>
                </div>

                <div className="landing-track-form-wrapper">
                    <form onSubmit={handleTrack} className="landing-track-form">
                        <div className="landing-track-input-group">
                            <Search className="landing-track-input-icon" />
                            <input
                                type="text"
                                value={trackingId}
                                onChange={(e) => setTrackingId(e.target.value)}
                                placeholder="Enter Request ID (e.g., EMG-12345)"
                                className="landing-track-input"
                                disabled={isLoading}
                            />
                        </div>
                        <button
                            type="submit"
                            className="landing-track-btn"
                            disabled={isLoading}
                        >
                            {isLoading ? 'Searching...' : 'Track Request'}
                        </button>
                    </form>

                    {error && (
                        <div className="landing-track-error">
                            {error}
                        </div>
                    )}
                </div>

                <div className="landing-track-info">
                    <div className="landing-track-info-item">
                        <MapPin className="landing-track-info-icon" />
                        <div>
                            <strong>Live Location</strong>
                            <span>See ambulance position in real time</span>
                        </div>
                    </div>
                    <div className="landing-track-info-item">
                        <Clock className="landing-track-info-icon" />
                        <div>
                            <strong>Status Updates</strong>
                            <span>Get instant updates on ambulance arrival</span>
                        </div>
                    </div>
                    <div className="landing-track-info-item">
                        <CheckCircle className="landing-track-info-icon" />
                        <div>
                            <strong>Complete Visibility</strong>
                            <span>Track from dispatch to pickup</span>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}

export default LandingTrackSection;

