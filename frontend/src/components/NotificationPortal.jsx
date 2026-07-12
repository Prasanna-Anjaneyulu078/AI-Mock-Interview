import React from 'react';
import GlobalNotification from './GlobalNotification';

/**
 * Fixed, top-center overlay rendered once at the app level so the AI-provider
 * notification floats above every page (including the Coding IDE) at the same
 * position, instead of being embedded inside a page layout.
 */
const NotificationPortal = () => (
  <div className="gn-portal">
    <GlobalNotification />
  </div>
);

export default NotificationPortal;
