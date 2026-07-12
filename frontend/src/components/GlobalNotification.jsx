import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNotification } from './NotificationProvider';
import './GlobalNotification.css';

/* ------------------------------------------------------------------ */
/* Error -> notification mapping                                       */
/* ------------------------------------------------------------------ */

const normalizeProvider = (provider) => {
  if (!provider) return 'UNKNOWN';
  return String(provider).toUpperCase().replace(/\s+/g, '_');
};

const isTimeout = (error) => {
  const code = error.errorCode || '';
  const msg = (error.message || '').toLowerCase();
  return (
    error.status === 408 ||
    code === 'TIMEOUT' ||
    /timed?\s*out|timeout|did not respond|took too long/.test(msg)
  );
};

/**
 * Converts a backend provider-error object into the minimal payload consumed by
 * the notification UI: { provider, providerKey, status, type, fallback, questionSource, reason }.
 * The card resolves the friendly title/message from providerKey + type.
 */
export const mapAiErrorToNotification = (error) => {
  if (!error) return null;
  const providerKey = normalizeProvider(error.provider);
  const status = error.status;
  const errorCode = error.errorCode;
  const fallback = error.fallbackUsed || error.fallbackActivated === true;
  const questionSource = error.questionSource || (fallback ? 'Local Question Engine' : null);

  let type = 'error';
  if (providerKey === 'VALIDATION') {
    type = 'validation';
  } else if (errorCode === 'INSUFFICIENT_CREDITS' || status === 402) {
    type = 'quota';
  } else if (errorCode === 'RATE_LIMIT_EXCEEDED' || status === 429) {
    type = 'rate_limit';
  } else if (errorCode === 'AI_PROVIDER_LIMIT') {
    type = 'quota';
  } else if (errorCode === 'ACCESS_DENIED') {
    type = 'access_denied';
  } else if (isTimeout(error)) {
    type = 'timeout';
  } else if (errorCode === 'ALL_PROVIDERS_FAILED' || errorCode === 'NO_PROVIDERS_CONFIGURED') {
    type = 'all_down';
  } else if (errorCode === 'INVALID_API_KEY') {
    type = 'auth_error';
  } else if (errorCode === 'SUBSCRIPTION_INACTIVE') {
    type = 'subscription';
  } else if (errorCode === 'JUDGE0_FORBIDDEN') {
    type = 'forbidden';
  }

  return {
    provider: error.provider || 'Unknown Provider',
    providerKey,
    status,
    type,
    fallback,
    questionSource,
    reason: error.message,
  };
};

/* ------------------------------------------------------------------ */
/* Friendly, provider-specific copy                                    */
/* ------------------------------------------------------------------ */

const NOTIFICATION_LIBRARY = {
  OPENROUTER: {
    quota: {
      title: 'AI Service Temporarily Unavailable',
      message:
        "OpenRouter has reached its available credit limit.\n\nWe've automatically switched to backup interview questions so your interview can continue without interruption.",
    },
    rate_limit: {
      title: 'AI Service Temporarily Busy',
      message: "OpenRouter rate limits have been reached.\n\nBackup interview questions are now being used automatically.",
    },
    default: {
      title: 'AI Service Temporarily Unavailable',
      message:
        "OpenRouter is temporarily unavailable.\n\nWe've switched to backup interview questions so your interview can continue.",
    },
  },
  GROQ: {
    rate_limit: {
      title: 'AI Service Temporarily Busy',
      message: 'Groq rate limits have been reached.\n\nBackup interview questions are now being used automatically.',
    },
    default: {
      title: 'AI Service Temporarily Busy',
      message: 'Groq is temporarily busy.\n\nBackup interview questions are now being used automatically.',
    },
  },
  GEMINI: {
    quota: {
      title: 'AI Service Limit Reached',
      message: "Gemini has exceeded its current quota.\n\nThe interview will continue using backup questions.",
    },
    rate_limit: {
      title: 'AI Service Limit Reached',
      message: "Gemini has exceeded its current quota.\n\nThe interview will continue using backup questions.",
    },
    default: {
      title: 'AI Service Limit Reached',
      message: "Gemini is temporarily unavailable.\n\nThe interview will continue using backup questions.",
    },
  },
  VALIDATION: {
    default: { title: 'Notice', message: null },
  },
  JUDGE0: {
    auth_error: {
      title: '403 Forbidden',
      message: 'Invalid API key or inactive subscription.',
    },
    subscription: {
      title: '403 Forbidden',
      message: 'Invalid API key or inactive subscription.',
    },
    forbidden: {
      title: '403 Forbidden',
      message: 'Invalid API key or inactive subscription.',
    },
    default: {
      title: 'Judge0 Error',
      message: 'Judge0 service unavailable.',
    },
  },
  _generic: {
    timeout: {
      title: 'Connection Timeout',
      message: "The AI provider did not respond in time.\n\nBackup questions have been activated automatically.",
    },
    all_down: {
      title: 'AI Service Temporarily Unavailable',
      message:
        'Our AI providers are momentarily unavailable.\n\nBackup interview questions are being used so you can continue.',
    },
    access_denied: {
      title: 'AI Service Access Denied',
      message: 'Access to the AI provider was denied.\n\nBackup interview questions are being used automatically.',
    },
    default: {
      title: 'AI Service Notice',
      message: 'The AI service is temporarily unavailable.\n\nYour interview continues using backup questions.',
    },
  },
};

const BADGE_LABEL = {
  OPENROUTER: 'OPENROUTER',
  GROQ: 'GROQ',
  GEMINI: 'GEMINI',
  JUDGE0: 'JUDGE0',
  AI_SERVICES: 'AI SERVICES',
  ALL_PROVIDERS: 'AI SERVICES',
  VALIDATION: 'NOTICE',
  UNKNOWN: 'NOTICE',
};

/* ------------------------------------------------------------------ */
/* Component                                                           */
/* ------------------------------------------------------------------ */

const AUTO_HIDE_MS = 10000;
const EXIT_MS = 200;

const GlobalNotification = () => {
  const { notification, hideNotification } = useNotification();
  const [closing, setClosing] = useState(false);
  const timerRef = useRef(null);

  const requestClose = useCallback(() => {
    setClosing(true);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => hideNotification(), EXIT_MS);
  }, [hideNotification]);

  // Auto-hide after 10s; reset whenever a new notification arrives.
  useEffect(() => {
    if (!notification) return undefined;
    setClosing(false);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(requestClose, AUTO_HIDE_MS);
    return () => clearTimeout(timerRef.current);
  }, [notification, requestClose]);

  // ESC to dismiss.
  useEffect(() => {
    if (!notification) return undefined;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') requestClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [notification, requestClose]);

  if (!notification) return null;

  const lib = NOTIFICATION_LIBRARY[notification.providerKey] || NOTIFICATION_LIBRARY._generic;
  const content = lib[notification.type] || lib.default || NOTIFICATION_LIBRARY._generic.default;
  const title = content.title;
  const message = content.message || notification.reason || 'An unexpected issue occurred.';
  const severity = notification.fallback ? 'fallback' : 'critical';
  const badge = BADGE_LABEL[notification.providerKey] || 'AI SERVICES';

  return (
    <div
      className={`gn-card gn-card--${severity}${closing ? ' gn-card--closing' : ''}`}
      role="alert"
      aria-live="assertive"
    >
      <span className="gn-card__accent" aria-hidden="true" />

      <div className="gn-card__header">
        <span className="gn-badge">
          <span className="gn-badge__icon" aria-hidden="true" />
          {badge}
        </span>
        <button
          type="button"
          className="gn-card__close"
          onClick={requestClose}
          aria-label="Dismiss notification"
        >
          &times;
        </button>
      </div>

      <div className="gn-card__body">
        <span className="gn-card__icon" aria-hidden="true">
          {severity === 'fallback' ? 'i' : '!'}
        </span>
        <div className="gn-card__content">
          <h2 className="gn-card__title">{title}</h2>
          <p className="gn-card__message">{message}</p>
          {notification.fallback && notification.questionSource && (
            <p className="gn-card__meta">Question Source: {notification.questionSource}</p>
          )}
        </div>
      </div>

      <div className="gn-card__actions">
        <button type="button" className="gn-card__continue" onClick={requestClose}>
          Continue Interview
        </button>
      </div>
    </div>
  );
};

export default GlobalNotification;
