import { useState, useEffect } from 'react';
import { BsMicFill } from 'react-icons/bs';
import './index.css';

const SILENCE_TIMEOUT = 3000;

function ConversationalMic({ onTranscriptReady, onAutoSubmit, disabled }) {
  const [isListening, setIsListening] = useState(false);
  const [liveText, setLiveText] = useState('');
  const [finalText, setFinalText] = useState('');
  const [isSupported, setIsSupported] = useState(true);
  const [autoSubmitCountdown, setAutoSubmitCountdown] = useState(null);
  const [recognition, setRecognition] = useState(null);
  const [silenceTimer, setSilenceTimer] = useState(null);

  useEffect(() => {
  const SpeechRecognition =
    window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    setIsSupported(false);
    return;
  }

  const recog = new SpeechRecognition();
  recog.continuous = true;
  recog.interimResults = true;
  recog.lang = 'en-US';

  let accumulated = '';

  recog.onresult = (event) => {
    let interim = '';
    let final = '';

    for (let i = 0; i < event.results.length; i++) {
      const transcript = event.results[i][0].transcript;
      if (event.results[i].isFinal) {
        final += transcript + ' ';
      } else {
        interim = transcript;
      }
    }

    if (final) {
      accumulated = final.trim();
      setFinalText(accumulated);
    }
    setLiveText(interim);

    clearSilenceTimer();
    startSilenceTimer(accumulated || interim);
  };

  recog.onerror = (event) => {
    console.error('Speech recognition error:', event.error);
    if (event.error === 'no-speech') {
      return;
    }
    setIsListening(false);
  };

  recog.onend = () => {
    setIsListening(false);
  };

  setRecognition(recog);

  return () => {
    recog.abort();
    clearSilenceTimer();
  };
}, []);

  useEffect(() => {
    if (recognition && isSupported && !disabled) {
      startListening();
    }
  }, [recognition, isSupported, disabled]);

  const clearSilenceTimer = () => {
    if (silenceTimer) {
      clearTimeout(silenceTimer);
      setSilenceTimer(null);
    }
    setAutoSubmitCountdown(null);
  };

  const startSilenceTimer = (currentText) => {
    const timer = setTimeout(() => {
      if (currentText && currentText.trim().length > 0) {
        handleAutoSubmit(currentText.trim());
      }
    }, SILENCE_TIMEOUT);
    setSilenceTimer(timer);

    setAutoSubmitCountdown(3);
    setTimeout(() => setAutoSubmitCountdown(2), 1000);
    setTimeout(() => setAutoSubmitCountdown(1), 2000);
  };

  const startListening = () => {
    if (!recognition) return;
    try {
      setLiveText('');
      setFinalText('');
      setAutoSubmitCountdown(null);
      recognition.start();
      setIsListening(true);
    } catch (error) {
      console.error('Recognition start error:', error.message);
    }
  };

  const stopListening = () => {
    if (!recognition) return;
    try {
      recognition.stop();
    } catch (error) {
      // Already stopped
    }
    setIsListening(false);
    clearSilenceTimer();
  };

  const handleAutoSubmit = (text) => {
    stopListening();
    if (onAutoSubmit) {
      onAutoSubmit(text);
    }
  };

  const handleManualSubmit = () => {
    const text = (finalText + ' ' + liveText).trim();
    if (!text) return;
    stopListening();
    if (onAutoSubmit) {
      onAutoSubmit(text);
    }
  };

  const handleRestart = () => {
    setLiveText('');
    setFinalText('');
    clearSilenceTimer();
    startListening();
  };

  const displayText = (finalText + ' ' + liveText).trim();

  if (!isSupported) {
    return (
      <div className="cm-unsupported">
        <p className="cm-unsupported-text">
          Voice recognition is not supported in this browser. Please use Chrome
          or Edge.
        </p>
      </div>
    );
  }

  return (
    <div className="cm-container">
      <div className="cm-mic-button-wrapper">
        <button
          className={`cm-mic-button ${isListening ? 'cm-mic-active' : ''} ${disabled ? 'cm-mic-button-disabled' : ''}`}
          onClick={isListening ? stopListening : startListening}
          disabled={disabled}
        >
          <BsMicFill className="cm-mic-icon" />
        </button>
      </div>

      <div className="cm-status">
        {isListening && !displayText && (
          <p className="cm-status-listening">Listening...</p>
        )}
        {isListening && displayText && !autoSubmitCountdown && (
          <p className="cm-status-listening">Hearing you...</p>
        )}
        {autoSubmitCountdown && (
          <p className="cm-status-countdown">
            Submitting in {autoSubmitCountdown}s...{' '}
            <button
              className="cm-keep-talking-btn"
              onClick={() => {
                clearSilenceTimer();
                startListening();
              }}
            >
              Keep talking
            </button>
          </p>
        )}
        {!isListening && displayText && (
          <p className="cm-status-done">Done</p>
        )}
        {!isListening && !displayText && (
          <p className="cm-status-ready">Ready to listen</p>
        )}
      </div>

      {displayText && (
        <div className="cm-transcript-box">
          <span className="cm-transcript-final">{finalText}</span>
          {liveText && (
            <span className="cm-transcript-interim">{liveText}</span>
          )}
        </div>
      )}

      <div className="cm-controls">
        {isListening ? (
          <button
            className={`cm-submit-btn ${!displayText ? 'cm-submit-btn-disabled' : ''}`}
            onClick={handleManualSubmit}
            disabled={!displayText}
          >
            Submit Answer
          </button>
        ) : displayText ? (
          <div className="cm-done-actions">
            <button className="cm-speak-again-btn" onClick={handleRestart}>
              Speak Again
            </button>
            <button className="cm-submit-btn" onClick={handleManualSubmit}>
              Submit Answer
            </button>
          </div>
        ) : (
          <button
            className={`cm-start-btn ${disabled ? 'cm-start-btn-disabled' : ''}`}
            onClick={startListening}
            disabled={disabled}
          >
            Start listening
          </button>
        )}
      </div>
    </div>
  );
}

export default ConversationalMic;
