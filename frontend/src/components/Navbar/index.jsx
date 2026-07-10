import { useState, useContext } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { AuthContext } from '../../context/AuthContext.jsx';
import { useTheme } from '../../hooks/useTheme.js';
import { BsCameraVideo, BsList, BsX, BsMoonFill, BsSunFill, BsDisplay } from 'react-icons/bs';
import { MdDashboard, MdHistory, MdLogout } from 'react-icons/md';
import { FaUser } from 'react-icons/fa';
import './index.css';

function Navbar() {
  const { user, logout } = useContext(AuthContext);
  const { theme, toggleTheme, getActiveTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
    setMenuOpen(false);
  };

  const handleNavClick = () => {
    setMenuOpen(false);
  };

  const renderThemeIcon = () => {
    if (theme === 'light') return <BsSunFill className="theme-icon" />;
    if (theme === 'dark') return <BsMoonFill className="theme-icon" />;
    return <BsDisplay className="theme-icon" />;
  };

  return (
    <nav className="navbar-container">
      <div className="navbar-left">
        <Link to="/" className="navbar-brand" onClick={handleNavClick}>
          <BsCameraVideo className="brand-icon" />
          <span className="brand-text">AI Mock Interview</span>
        </Link>
        <div className="nav-links">
          <Link
            to="/dashboard"
            className={`nav-link ${location.pathname === '/dashboard' ? 'nav-link-active' : ''}`}
          >
            <MdDashboard className="nav-link-icon" />
            Dashboard
          </Link>
          <Link
            to="/history"
            className={`nav-link ${location.pathname === '/history' ? 'nav-link-active' : ''}`}
          >
            <MdHistory className="nav-link-icon" />
            History
          </Link>
        </div>
      </div>

      <div className="navbar-right">
        <button className="theme-toggle-btn" onClick={toggleTheme} aria-label="Toggle theme">
          {renderThemeIcon()}
        </button>
        {user && (
          <div className="navbar-user-section">
            <FaUser className="user-icon" />
            <span className="user-name">{user.name}</span>
            <button className="logout-btn" onClick={handleLogout}>
              <MdLogout className="logout-icon" />
              Logout
            </button>
          </div>
        )}
        {/* Hamburger toggle — only visible on mobile */}
        <button
          className="navbar-hamburger"
          onClick={() => setMenuOpen((prev) => !prev)}
          aria-label="Toggle menu"
        >
          {menuOpen ? <BsX className="hamburger-icon" /> : <BsList className="hamburger-icon" />}
        </button>
      </div>

      {/* Mobile dropdown menu */}
      {menuOpen && (
        <div className="navbar-mobile-menu">
          <Link
            to="/dashboard"
            className={`mobile-nav-link ${location.pathname === '/dashboard' ? 'mobile-nav-link-active' : ''}`}
            onClick={handleNavClick}
          >
            <MdDashboard className="mobile-nav-icon" />
            Dashboard
          </Link>
          <Link
            to="/history"
            className={`mobile-nav-link ${location.pathname === '/history' ? 'mobile-nav-link-active' : ''}`}
            onClick={handleNavClick}
          >
            <MdHistory className="mobile-nav-icon" />
            History
          </Link>
          
          <button className="mobile-theme-toggle" onClick={toggleTheme}>
            {renderThemeIcon()}
            <span>Theme: {theme.charAt(0).toUpperCase() + theme.slice(1)}</span>
          </button>

          {user && (
            <div className="mobile-user-section">
              <div className="mobile-user-info">
                <FaUser className="mobile-user-icon" />
                <span className="mobile-user-name">{user.name}</span>
              </div>
              <button className="mobile-logout-btn" onClick={handleLogout}>
                <MdLogout className="mobile-logout-icon" />
                Logout
              </button>
            </div>
          )}
        </div>
      )}
    </nav>
  );
}

export default Navbar;
