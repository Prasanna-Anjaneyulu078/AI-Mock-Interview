// ============================================
// AuthContext.jsx - Authentication State
// ============================================
// Manages user login state across the app.
// Reference: createContext, useState, useEffect - reference-react.md
// ============================================

import { createContext, useState, useEffect } from 'react';
import { getMe } from '../services/authService.js';

const AuthContext = createContext(null);

function AuthProvider({ children }) {
  // State: current user data and loading status
  // Reference: useState hook - reference-react.md
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On mount: check if user is already logged in via stored token
  // Reference: useEffect hook - reference-react.md
  useEffect(() => {
    const checkAuth = async () => {
      const token = localStorage.getItem('token');
      if (!token || token === 'undefined' || token === 'null') {
        setLoading(false);
        return;
      }

      try {
        const userData = await getMe();
        setUser(userData);
      } catch (error) {
        // Token may be expired; clear both tokens so the api interceptor
        // can't attempt a refresh with a missing refresh token.
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
      }
      setLoading(false);
    };

    const handleForceLogout = () => {
      logoutUser();
    };

    window.addEventListener('auth:logout', handleForceLogout);
    checkAuth();

    return () => {
      window.removeEventListener('auth:logout', handleForceLogout);
    };
  }, []);

  // Login: save access + refresh tokens and set user
  const login = (token, refreshToken, userData) => {
    localStorage.setItem('token', token);
    if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
    setUser(userData);
  };

  // Logout: remove both tokens and clear user
  const logoutUser = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout: logoutUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export { AuthContext, AuthProvider };
