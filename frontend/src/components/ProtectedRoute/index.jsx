// ============================================
// ProtectedRoute - Auth Guard Component
// ============================================
// Redirects to /login if user is not authenticated.
// Reference: useContext, Navigate - reference-react.md
// ============================================

import { useContext } from 'react';
import { Navigate } from 'react-router-dom';
import { AuthContext } from '../../context/AuthContext.jsx';
import './index.css';

function ProtectedRoute({ children }) {
  const { user, loading } = useContext(AuthContext);

  // Show loading state while checking auth
  if (loading) {
    return (
      <div className="protected-loading">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
        <p className="protected-loading-text">Loading...</p>
      </div>
    );
  }

  // Redirect to login if not authenticated
  if (!user) return <Navigate to="/login" />;

  return <div className="protected-shell">{children}</div>;
}

export default ProtectedRoute;
