import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import LoadingSpinner from '../components/LoadingSpinner';
import './LoginPage.css';

const RegisterPage = () => {
  const navigate = useNavigate();
  const [formState, setFormState] = useState({
    username: '',
    email: '',
    password: '',
    role: 'DISPATCHER',
    ambulanceId: '',
    isLoading: false,
    error: null,
    success: null,
  });

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormState((prev) => ({
      ...prev,
      [name]: value,
      error: null,
      success: null,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!formState.username || !formState.email || !formState.password || !formState.role) {
      setFormState((prev) => ({
        ...prev,
        error: 'Please fill all required fields',
      }));
      return;
    }

    if (formState.role === 'AMBULANCE_DRIVER' && !formState.ambulanceId.trim()) {
      setFormState((prev) => ({
        ...prev,
        error: 'Ambulance ID is required for driver registration',
      }));
      return;
    }

    setFormState((prev) => ({ ...prev, isLoading: true, error: null, success: null }));

    try {
      const payload = {
        username: formState.username.trim(),
        email: formState.email.trim(),
        password: formState.password,
        role: formState.role,
      };

      if (formState.role === 'AMBULANCE_DRIVER' && formState.ambulanceId.trim()) {
        payload.ambulanceId = formState.ambulanceId.trim();
      }

      const response = await fetch('http://localhost:8080/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.error || error.message || 'Registration failed');
      }

      setFormState((prev) => ({
        ...prev,
        isLoading: false,
        success: 'Registration successful! Redirecting to login...',
      }));

      setTimeout(() => navigate('/login'), 2000);
    } catch (error) {
      setFormState((prev) => ({
        ...prev,
        isLoading: false,
        error: error.message || 'Registration failed',
      }));
    }
  };


  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <h1 className="auth-title">Create Account</h1>
          <p className="auth-subtitle">Join the emergency response team</p>

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-form-group">
              <label htmlFor="username" className="auth-form-label">Username</label>
              <input
                type="text"
                id="username"
                name="username"
                value={formState.username}
                onChange={handleInputChange}
                disabled={formState.isLoading}
                className="auth-form-input"
                placeholder="Choose a username"
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="email" className="auth-form-label">Email</label>
              <input
                type="email"
                id="email"
                name="email"
                value={formState.email}
                onChange={handleInputChange}
                disabled={formState.isLoading}
                className="auth-form-input"
                placeholder="Enter your email"
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="password" className="auth-form-label">Password</label>
              <input
                type="password"
                id="password"
                name="password"
                value={formState.password}
                onChange={handleInputChange}
                disabled={formState.isLoading}
                className="auth-form-input"
                placeholder="Minimum 8 characters"
                autoComplete="new-password"
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="role" className="auth-form-label">Role</label>
              <select
                id="role"
                name="role"
                value={formState.role}
                onChange={handleInputChange}
                disabled={formState.isLoading}
                className="auth-form-select"
              >
                <option value="DISPATCHER">Dispatcher</option>
                <option value="AMBULANCE_DRIVER">Ambulance Driver</option>
              </select>
            </div>

            {formState.role === 'AMBULANCE_DRIVER' && (
              <div className="auth-form-group">
                <label htmlFor="ambulanceId" className="auth-form-label">Ambulance ID</label>
                <input
                  type="text"
                  id="ambulanceId"
                  name="ambulanceId"
                  value={formState.ambulanceId}
                  onChange={handleInputChange}
                  disabled={formState.isLoading}
                  className="auth-form-input"
                  placeholder="e.g. AMB-001"
                  required={formState.role === 'AMBULANCE_DRIVER'}
                />
              </div>
            )}

            {formState.error && (
              <div className="auth-error" role="alert">{formState.error}</div>
            )}

            {formState.success && (
              <div className="auth-success" role="status">{formState.success}</div>
            )}

            <button type="submit" disabled={formState.isLoading} className="auth-submit-btn">
              {formState.isLoading ? (
                <span className="auth-button-content">
                  <LoadingSpinner size="small" />
                  <span>Creating account...</span>
                </span>
              ) : (
                'Create Account'
              )}
            </button>
          </form>

          <p className="auth-switch">
            Already have an account? <Link to="/login">Sign In</Link>
          </p>

          <p className="auth-home-link">
            <Link to="/">← Back to Home</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
