import { useState, useEffect } from 'react';
import Editor from '@monaco-editor/react';
import { BsArrowsFullscreen, BsFullscreenExit, BsMoonFill, BsSunFill, BsClockHistory } from 'react-icons/bs';
import './index.css';

function CodeEditor({ value, onChange, language, readOnly }) {
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [theme, setTheme] = useState('light');
  const [timeElapsed, setTimeElapsed] = useState(0);

  // Simple local timer for this code problem
  useEffect(() => {
    const timer = setInterval(() => {
      setTimeElapsed(prev => prev + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const formatTime = (seconds) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  const toggleFullscreen = () => setIsFullscreen(!isFullscreen);
  const toggleTheme = () => setTheme(prev => prev === 'light' ? 'vs-dark' : 'light');

  return (
    <div className={`code-editor-wrapper ${isFullscreen ? 'code-editor-fullscreen' : ''}`}>
      <div className="code-editor-toolbar">
        <div className="toolbar-left">
          <span className="editor-lang-badge">{language || 'javascript'}</span>
          <span className="editor-timer">
            <BsClockHistory className="timer-icon" /> {formatTime(timeElapsed)}
          </span>
        </div>
        <div className="toolbar-right">
          <button className="toolbar-btn" onClick={toggleTheme} title="Toggle Theme">
            {theme === 'light' ? <BsMoonFill /> : <BsSunFill />}
          </button>
          <button className="toolbar-btn" onClick={toggleFullscreen} title="Toggle Fullscreen">
            {isFullscreen ? <BsFullscreenExit /> : <BsArrowsFullscreen />}
          </button>
        </div>
      </div>
      <Editor
        height={isFullscreen ? "calc(100vh - 40px)" : "400px"}
        language={language || 'javascript'}
        theme={theme}
        value={value}
        onChange={onChange}
        options={{
          readOnly: readOnly || false,
          minimap: { enabled: false },
          fontSize: 14,
          fontFamily: "monospace",
          wordWrap: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: 2,
          bracketPairColorization: { enabled: true },
          padding: { top: 12 },
        }}
      />
    </div>
  );
}

export default CodeEditor;
