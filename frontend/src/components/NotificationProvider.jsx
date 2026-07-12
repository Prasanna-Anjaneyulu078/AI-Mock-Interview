import React, { createContext, useState, useCallback, useContext } from 'react';

/**
 * Global notification state. A single notification is shown at the app level
 * (via NotificationPortal) so it overlays every page — including the Coding IDE —
 * with the same position, instead of being buried inside page layouts.
 */
const NotificationContext = createContext(null);

export function NotificationProvider({ children }) {
  const [notification, setNotification] = useState(null);

  const showNotification = useCallback((payload) => {
    if (!payload) return;
    setNotification(payload);
  }, []);

  const hideNotification = useCallback(() => {
    setNotification(null);
  }, []);

  return (
    <NotificationContext.Provider value={{ notification, showNotification, hideNotification }}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotification() {
  const ctx = useContext(NotificationContext);
  if (!ctx) {
    throw new Error('useNotification must be used within a NotificationProvider');
  }
  return ctx;
}

export default NotificationProvider;
