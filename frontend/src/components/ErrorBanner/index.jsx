import React, { useState, useEffect } from 'react';
import './ErrorBanner.css';

const ErrorBanner = ({ message, type = 'error', onClose }) => {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    setVisible(true);
    // Optional auto-hide after 5 seconds if you want
    // const timer = setTimeout(() => setVisible(false), 5000);
    // return () => clearTimeout(timer);
  }, [message]);

  if (!message || !visible) return null;

  return (
    <div className={`error-banner banner-${type}`}>
      <span className="banner-icon">
        {type === 'error' ? '⚠️' : 'ℹ️'}
      </span>
      <span className="banner-message">{message}</span>
      {onClose && (
        <button className="banner-close" onClick={() => {
          setVisible(false);
          onClose();
        }}>
          &times;
        </button>
      )}
    </div>
  );
};

export default ErrorBanner;
