import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';
import './LoginPage.css';

/**
 * LoginPage component for user authentication
 * Provides username/password inputs, form submission, error display, and redirect on success
 */
const LoginPage = () => {
  const { login } = useAuth();
  const [formState, setFormState] = useState({
    username: '',
    password: '',
    isLoading: false,
    error: null,
  });

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormState(prev => ({
      ...prev,
      [name]: value,
      error: null, // Clear error when user types
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Basic validation
    if (!formState.username || !formState.password) {
      setFormState(prev => ({
        ...prev,
        error: 'Please enter both username and password',
      }));
      return;
    }

    setFormState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      await login(formState.username, formState.password);
      // Navigation is handled by AuthContext after successful login
    } catch (error) {
      setFormState(prev => ({
        ...prev,
        isLoading: false,
        error: error.message || 'Login failed. Please check your credentials.',
      }));
    }
  };

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-card">
          <h1 className="login-title">Emergency Dispatch System</h1>
          <p className="login-subtitle">Sign in to continue</p>

          <form onSubmit={handleSubmit} className="login-form">
            <div className="form-group">
              <label htmlFor="username" className="form-label">
                Username
              </label>
              <input
                type="text"
                id="username"
                name="username"
                value={formState.username}
                onChange={handleInputChange}
                disabled={formState.isLoading}
                className="form-input"
                placeholder="Enter your username"
                autoComplete="username"
              />
            </div>

            <div className="form-group">
              <label htmlFor="password" className="form-label">
                Password
              </label>
              <input
                type="password"
                id="password"
                name="password"
                value={formState.password}
                onChange={handleInputChange}
                disabled={formState.isLoading}
                className="form-input"
                placeholder="Enter your password"
                autoComplete="current-password"
              />
            </div>

            {formState.error && (
              <div className="error-message" role="alert">
                {formState.error}
              </div>
            )}

            <button
              type="submit"
              disabled={formState.isLoading}
              className="submit-button"
            >
              {formState.isLoading ? (
                <span className="button-content">
                  <LoadingSpinner size="small" />
                  <span>Signing in...</span>
                </span>
              ) : (
                'Sign In'
              )}
            </button>
          </form>

          <p className="auth-switch-text">
            Need an account? <Link to="/register">Register</Link>
          </p>
          <p className="auth-switch-text">
            Want the overview first? <Link to="/">View Home</Link>
          </p>
          <p className="auth-switch-text">
            Need help now? <Link to="/citizen">Request Emergency</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
