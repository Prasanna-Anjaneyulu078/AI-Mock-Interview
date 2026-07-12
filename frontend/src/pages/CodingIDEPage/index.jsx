import React, { useState, useEffect, useRef } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';
import Editor from '@monaco-editor/react';
import { BsPlayFill, BsCloudUploadFill, BsArrowsAngleExpand, BsArrowsAngleContract, BsCheckCircleFill, BsXCircleFill, BsPauseFill, BsArrowClockwise, BsStopFill } from 'react-icons/bs';
import { useTheme } from '../../hooks/useTheme.js';
import ErrorBanner from '../../components/ErrorBanner';
import { useNotification } from '../../components/NotificationProvider';
import { mapAiErrorToNotification } from '../../components/GlobalNotification';
import ConfirmModal from '../../components/ConfirmModal';
import { endInterview } from '../../services/interviewService.js';
import './CodingIDEPage.css';

const CodingIDEPage = () => {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  const [question, setQuestion] = useState(null);
  const [loading, setLoading] = useState(true);
  const [bannerMessage, setBannerMessage] = useState(null);
  const { showNotification } = useNotification();
  
  const { theme: globalTheme } = useTheme();
  const [language, setLanguage] = useState('java');
  const [sourceCode, setSourceCode] = useState('');
  
  const [isFullscreen, setIsFullscreen] = useState(false);
  
  const [submitting, setSubmitting] = useState(false);
  const [running, setRunning] = useState(false);
  const [ending, setEnding] = useState(false);
  const [showEndModal, setShowEndModal] = useState(false);
  const [showSubmitModal, setShowSubmitModal] = useState(false);
  
  const [runResult, setRunResult] = useState(null);
  const [activeTab, setActiveTab] = useState('console'); // 'console' | 'testcases'
  const [activeTestCaseIndex, setActiveTestCaseIndex] = useState(0);

  const [evaluation, setEvaluation] = useState(null);
  const [timer, setTimer] = useState(0);
  const [isTimerPaused, setIsTimerPaused] = useState(false);

  // Persist timer so it is preserved across re-renders, theme switches, and page refreshes.
  const TIMER_STORAGE_KEY = `coding-ide-timer-${id || 'default'}`;

  const initialized = React.useRef(false);

  /* ----------------------------- Resizable layout ----------------------------- */
  const SPLIT_STORAGE_KEY = 'coding-ide-left-width';
  const MIN_LEFT = 30;       // minimum left panel width (%)
  const MIN_RIGHT = 40;      // minimum right panel width (%)
  const MAX_LEFT = 100 - MIN_RIGHT; // 60 — keeps right panel >= 40%
  const KEY_STEP = 2;        // keyboard nudge step (%)

  const clampWidth = (pct) => Math.min(MAX_LEFT, Math.max(MIN_LEFT, pct));

  const [leftWidthPct, setLeftWidthPct] = useState(() => {
    const saved = parseFloat(localStorage.getItem(SPLIT_STORAGE_KEY));
    return Number.isFinite(saved) ? clampWidth(saved) : 40; // default 40/60
  });
  const [isDragging, setIsDragging] = useState(false);

  const bodyRef = useRef(null);
  const isDraggingRef = useRef(false);
  const leftWidthRef = useRef(leftWidthPct);
  leftWidthRef.current = leftWidthPct;

  const applyWidth = (pct) => {
    const clamped = clampWidth(pct);
    leftWidthRef.current = clamped;
    setLeftWidthPct(clamped);
  };

  const onResizeStart = (e) => {
    e.preventDefault();
    isDraggingRef.current = true;
    setIsDragging(true);
    document.body.classList.add('ide-resizing');
  };

  const persistWidth = () => {
    localStorage.setItem(SPLIT_STORAGE_KEY, leftWidthRef.current.toString());
  };

  useEffect(() => {
    const handleMove = (e) => {
      if (!isDraggingRef.current || !bodyRef.current) return;
      const rect = bodyRef.current.getBoundingClientRect();
      const clientX = (e.touches && e.touches.length) ? e.touches[0].clientX : e.clientX;
      if (e.touches) e.preventDefault(); // prevent page scroll while dragging on touch
      const pct = ((clientX - rect.left) / rect.width) * 100;
      applyWidth(pct);
    };
    const handleUp = () => {
      if (!isDraggingRef.current) return;
      isDraggingRef.current = false;
      setIsDragging(false);
      document.body.classList.remove('ide-resizing');
      persistWidth();
    };
    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
    window.addEventListener('touchmove', handleMove, { passive: false });
    window.addEventListener('touchend', handleUp);
    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
      window.removeEventListener('touchmove', handleMove);
      window.removeEventListener('touchend', handleUp);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Keyboard-accessible splitter (arrows nudge, Home/End jump to extremes)
  const onSplitterKeyDown = (e) => {
    let handled = true;
    switch (e.key) {
      case 'ArrowLeft':  applyWidth(leftWidthRef.current - KEY_STEP); break;
      case 'ArrowRight': applyWidth(leftWidthRef.current + KEY_STEP); break;
      case 'Home':       applyWidth(MIN_LEFT); break;
      case 'End':        applyWidth(MAX_LEFT); break;
      default: handled = false;
    }
    if (handled) {
      e.preventDefault();
      persistWidth();
    }
  };

  /* ----------------------------- Mobile tabs ----------------------------- */
  const [mobileTab, setMobileTab] = useState('problem'); // 'problem' | 'code' | 'testcases'

  const scrollToSection = (id) => {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;

    const startModule = async () => {
      try {
        const token = localStorage.getItem('token');
        const payload = id ? { interviewId: id } : {};
        const response = await axios.post(`/api/coding-module/start`, payload, { 
          headers: { Authorization: `Bearer ${token}` } 
        });
        const q = response.data;
        setQuestion(q);

        // Restore the language the candidate used last in this session, else default to Java.
        const savedLang = (() => {
          try { return localStorage.getItem(LANG_STORAGE_KEY); } catch (_) { return null; }
        })();
        const initialLang = savedLang && isValidLang(savedLang) ? savedLang : 'java';
        setLanguage(initialLang);

        // Seed editor with correct template for initial language
        setSourceCode(resolveStarterCode(q, initialLang));
      } catch (err) {
        console.error('Failed to load coding interview.', err);
        const respData = err.response?.data;
        if (respData && respData.fallbackUsed && respData.fallbackData) {
          const fallbackQuestion = respData.fallbackData;
          setQuestion(fallbackQuestion);
          showNotification(mapAiErrorToNotification(respData));
          setBannerMessage(null);

          const savedLang = (() => {
            try { return localStorage.getItem(LANG_STORAGE_KEY); } catch (_) { return null; }
          })();
          const initialLang = savedLang && isValidLang(savedLang) ? savedLang : 'java';
          setLanguage(initialLang);
          setSourceCode(resolveStarterCode(fallbackQuestion, initialLang));
        } else {
          setBannerMessage(respData?.message || 'Failed to load coding interview.');
        }
      } finally {
        setLoading(false);
      }
    };
    startModule();
  }, [id]);

  useEffect(() => {
    let interval;
    if (!loading && !evaluation && !isTimerPaused) {
      interval = setInterval(() => {
        setTimer(t => t + 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [loading, evaluation, isTimerPaused]);

  // Restore elapsed time + pause state on mount (e.g. after a page refresh).
  useEffect(() => {
    const saved = sessionStorage.getItem(TIMER_STORAGE_KEY);
    if (!saved) return;
    try {
      const parsed = JSON.parse(saved);
      if (typeof parsed.elapsed === 'number') setTimer(parsed.elapsed);
      if (typeof parsed.paused === 'boolean') setIsTimerPaused(parsed.paused);
    } catch (_) {
      /* ignore malformed storage */
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Persist elapsed time + pause state on every change.
  useEffect(() => {
    sessionStorage.setItem(
      TIMER_STORAGE_KEY,
      JSON.stringify({ elapsed: timer, paused: isTimerPaused })
    );
  }, [timer, isTimerPaused, TIMER_STORAGE_KEY]);

  // Clear persisted timer once the interview is submitted/evaluated.
  useEffect(() => {
    if (evaluation) sessionStorage.removeItem(TIMER_STORAGE_KEY);
  }, [evaluation, TIMER_STORAGE_KEY]);

  // Surface an AI error passed in via navigation state as a global notification.
  useEffect(() => {
    if (location.state?.aiError) {
      showNotification(mapAiErrorToNotification(location.state.aiError));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Alias map: handles c++/cpp and other variant keys */
  const langAliases = {
    'c++': ['c++', 'cpp'], 'cpp': ['cpp', 'c++'],
    'javascript': ['javascript', 'js'], 'js': ['js', 'javascript'],
    'typescript': ['typescript', 'ts'], 'ts': ['ts', 'typescript'],
    'python': ['python', 'py'], 'py': ['py', 'python'],
  };

  /** Canonical language set shown in the IDE toolbar (spec: Java, Python, JS, C++, C). */
  const LANGUAGES = [
    { id: 'java', label: 'Java', monaco: 'java' },
    { id: 'python', label: 'Python', monaco: 'python' },
    { id: 'javascript', label: 'JavaScript', monaco: 'javascript' },
    { id: 'cpp', label: 'C++', monaco: 'cpp' },
    { id: 'c', label: 'C', monaco: 'c' },
  ];
  const LANG_STORAGE_KEY = `coding-ide-lang-${id || 'default'}`;
  const isValidLang = (l) => LANGUAGES.some((x) => x.id === l);
  const monacoLangFor = (l) => (LANGUAGES.find((x) => x.id === l) || {}).monaco || l;

  /** Returns canonical empty stub for a language when starterCode is missing */
  const defaultStarterStub = (lang) => {
    switch (lang) {
      case 'java':       return 'public int solve(int[] input) {\n    // Write your code here\n}';
      case 'python':     return 'def solve(input):\n    pass';
      case 'javascript': return 'function solve(input) {\n    // Write your code here\n}';
      case 'c++': case 'cpp': return '#include<bits/stdc++.h>\nusing namespace std;\n\nint solve(vector<int>& input) {\n    // Write your code here\n    return 0;\n}';
      case 'c':          return '#include<stdio.h>\n\nint solve(int* input) {\n    /* Write your code here */\n    return 0;\n}';
      case 'typescript': return 'function solve(input: number[]): number {\n    // Write your code here\n    return 0;\n}';
      case 'go':         return 'func solve(input []int) int {\n    // Write your code here\n    return 0\n}';
      default:           return `// Write your ${lang} solution here\n`;
    }
  };

  /** Resolves starter code for a question + language with alias fallback */
  const resolveStarterCode = (q, lang) => {
    if (!q) return '';
    const normalized = (lang || 'javascript').toLowerCase().trim();
    if (q.starterCode) {
      try {
        const map = JSON.parse(q.starterCode);
        const aliases = langAliases[normalized] || [normalized];
        for (const alias of aliases) {
          if (map[alias] != null) return map[alias];
        }
      } catch (_) {
        return q.starterCode; // plain string
      }
    }
    return defaultStarterStub(normalized);
  };

  const handleLanguageChange = (e) => {
    const newLang = e.target.value.toLowerCase().trim();
    if (!isValidLang(newLang)) return;
    setLanguage(newLang);
    setSourceCode(resolveStarterCode(question, newLang));
    // Persist the chosen language for the rest of the session (and across refreshes).
    try { localStorage.setItem(LANG_STORAGE_KEY, newLang); } catch (_) {}
  };

  const handleRun = async () => {
    setRunning(true);
    setRunResult(null);
    setActiveTab('console');
    try {
      const token = localStorage.getItem('token');
      const response = await axios.post(`/api/coding-module/${question.id}/run`, {
        sourceCode,
        language
      }, { headers: { Authorization: `Bearer ${token}` } });
      setRunResult(response.data);
      if (response.data.testCaseResults && response.data.testCaseResults.length > 0) {
        setActiveTab('testcases');
      }
    } catch (err) {
      const respData = err.response?.data;
      let errorMsg = err.response?.data?.message || err.message;
      if (respData?.provider === 'Judge0' && respData?.status === 403) {
        errorMsg = `Provider:\nJudge0\n\nStatus:\n403 Forbidden\n\nReason:\nInvalid API key or inactive subscription.`;
      }
      setRunResult({
        compileOutput: errorMsg,
        passed: false,
        statusDescription: 'Request Failed'
      });
    } finally {
      setRunning(false);
    }
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const token = localStorage.getItem('token');
      const submitRes = await axios.post(`/api/coding-module/${question.id}/submit`, {
        sourceCode,
        language
      }, { headers: { Authorization: `Bearer ${token}` } });
      const sub = submitRes.data;
      
      const evalRes = await axios.post(`/api/coding-module/evaluate/${sub.id}`, {}, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setEvaluation(evalRes.data);
    } catch (err) {
      const respData = err.response?.data;
      if (respData && respData.fallbackUsed && respData.fallbackData) {
        showNotification(mapAiErrorToNotification(respData));
        setEvaluation(respData.fallbackData);
      } else {
        let errorMsg = 'Error submitting code: ' + (respData?.message || err.message);
        if (respData?.provider === 'Judge0' && respData?.status === 403) {
          errorMsg = `Provider:\nJudge0\n\nStatus:\n403 Forbidden\n\nReason:\nInvalid API key or inactive subscription.`;
        }
        setRunResult({
          compileOutput: errorMsg,
          passed: false
        });
        setActiveTab('console');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleEndInterview = async () => {
    setEnding(true);
    try {
      // Backend saves all progress, submitted answers and coding submissions,
      // then generates the feedback report. Even if AI scoring fails, the
      // submitted code/answers are already persisted, so nothing is lost.
      await endInterview(id);
      navigate(`/feedback/${id}`);
    } catch (error) {
      // If endInterview fails (e.g. interview already completed), still navigate
      // because the feedback report may already exist.
      console.warn('endInterview error (may already be completed):', error);
      navigate(`/feedback/${id}`);
    } finally {
      setEnding(false);
    }
  };

  if (loading) {
    return <div className="loading-screen" style={{ padding: '2rem' }}><h2>Loading Coding IDE...</h2></div>;
  }
  if (!question) {
    return <div className="loading-screen" style={{ padding: '2rem' }}><h2>Error loading question.</h2></div>;
  }

  const formatTime = (secs) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div
      className={`leetcode-ide ${isFullscreen ? 'ide-fullscreen' : ''}`}
      data-mobile-tab={evaluation ? 'evaluation' : mobileTab}
    >
      <div className="ide-navbar">
        <div className="navbar-left">
          <span className="ide-logo">IDE</span>
        </div>

        <div className="navbar-right">
          {/* Persistent End Interview — visible on every coding question */}
          <button
            className="ide-end-btn"
            onClick={() => setShowEndModal(true)}
            disabled={ending}
          >
            {ending ? 'Ending…' : <><BsStopFill className="ide-end-icon" /> End Interview</>}
          </button>
          <button className="btn-icon" onClick={() => setIsFullscreen(!isFullscreen)}>
            {isFullscreen ? <BsArrowsAngleContract /> : <BsArrowsAngleExpand />}
          </button>
        </div>
      </div>

      <div
        className={`ide-body ${isDragging ? 'is-resizing' : ''}`}
        ref={bodyRef}
        style={{ '--left-width': `${leftWidthPct}%` }}
      >
        {/* Mobile tab bar (visible < 768px) */}
        {!evaluation && (
          <div className="mobile-tabbar" role="tablist" aria-label="Coding view sections">
            <button
              role="tab"
              aria-selected={mobileTab === 'problem'}
              className={mobileTab === 'problem' ? 'active' : ''}
              onClick={() => setMobileTab('problem')}
            >Problem</button>
            <button
              role="tab"
              aria-selected={mobileTab === 'code'}
              className={mobileTab === 'code' ? 'active' : ''}
              onClick={() => setMobileTab('code')}
            >Code</button>
            <button
              role="tab"
              aria-selected={mobileTab === 'testcases'}
              className={mobileTab === 'testcases' ? 'active' : ''}
              onClick={() => setMobileTab('testcases')}
            >Test Cases</button>
          </div>
        )}

        {/* Left Panel: Problem Description */}
        <div className="ide-left-panel">
          <div className="panel-header">
            <h3>Description</h3>
          </div>
          <div className="panel-content problem-description">
            <nav className="section-nav" aria-label="Jump to problem section">
              <button type="button" onClick={() => scrollToSection('problem-statement')}>Problem</button>
              <button type="button" onClick={() => scrollToSection('constraints')}>Constraints</button>
              <button type="button" onClick={() => scrollToSection('examples')}>Examples</button>
              {question.hints && (
                <button type="button" onClick={() => scrollToSection('hints')}>Hints</button>
              )}
            </nav>

            <h1 className="problem-title" id="problem-statement">{question.title}</h1>
            <div className={`difficulty-badge diff-${question.difficulty?.toLowerCase()}`}>
              {question.difficulty}
            </div>

            <div className="problem-text">
              <p>{question.description}</p>
            </div>

            {question.constraints && (
              <div className="problem-constraints" id="constraints">
                <strong>Constraints:</strong>
                <pre>{question.constraints}</pre>
              </div>
            )}

            {question.testCases && question.testCases.length > 0 ? (
              <div className="problem-examples" id="examples">
                <h3>Examples</h3>
                {question.testCases.filter(tc => !tc.isHidden).length > 0 ? (
                  question.testCases.filter(tc => !tc.isHidden).map((tc, idx) => (
                    <div key={idx} className="example-box">
                      <strong className="example-title">{tc.name || `Example ${idx + 1}`}</strong>
                      <div className="io-section">
                        <div className="io-field">
                          <span className="io-label">Input:</span>
                          <pre className="io-pre">{tc.input}</pre>
                        </div>
                        <div className="io-field">
                          <span className="io-label">Output:</span>
                          <pre className="io-pre">{tc.expectedOutput}</pre>
                        </div>
                      </div>
                    </div>
                  ))
                ) : (
                  <p className="muted-text">No visible test cases available.</p>
                )}
              </div>
            ) : (
              <div className="problem-examples" id="examples">
                <h3>Examples</h3>
                <p className="muted-text">No test cases available.</p>
              </div>
            )}

            {question.hints && (
              <div className="problem-hints" id="hints">
                <strong>Hints:</strong>
                <p>{question.hints}</p>
              </div>
            )}
          </div>
        </div>

        {/* Draggable resizer between panels */}
        <div
          className="ide-splitter"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize description and editor panels"
          aria-valuenow={Math.round(leftWidthPct)}
          aria-valuemin={MIN_LEFT}
          aria-valuemax={MAX_LEFT}
          tabIndex={0}
          onMouseDown={onResizeStart}
          onTouchStart={onResizeStart}
          onKeyDown={onSplitterKeyDown}
        />

        {/* Right Panel: Editor and Results */}
        {!evaluation ? (
          <div className="ide-right-panel">
            <ErrorBanner message={bannerMessage} onClose={() => setBannerMessage(null)} />
            <div className="editor-container">
              <div className="panel-header editor-header">
                <select className="ide-select" value={language} onChange={handleLanguageChange} disabled={running || submitting} aria-label="Programming language">
                  {LANGUAGES.map((l) => (
                    <option key={l.id} value={l.id}>{l.label}</option>
                  ))}
                </select>

                {/* Right-panel action bar: Run Code, Submit and Timer */}
                <div className="editor-actions">
                  <button className="btn-run" onClick={handleRun} disabled={running || submitting || evaluation}>
                    {running ? 'Running...' : <><BsPlayFill /> Run Code</>}
                  </button>
                  <button className="btn-submit" onClick={() => setShowSubmitModal(true)} disabled={submitting || running || evaluation}>
                    {submitting ? 'Submitting...' : <><BsCloudUploadFill /> Submit</>}
                  </button>

                  <div className="editor-timer">
                    <div className="timer-container">
                      <span className="timer-badge">
                        <span className="timer-icon" aria-hidden="true">⏱</span>
                        {formatTime(timer)}
                      </span>
                      {!evaluation && (
                        <div className="timer-controls">
                          {isTimerPaused ? (
                            <button className="timer-btn" onClick={() => setIsTimerPaused(false)} title="Resume Timer" aria-label="Resume Timer"><BsPlayFill /></button>
                          ) : (
                            <button className="timer-btn" onClick={() => setIsTimerPaused(true)} title="Pause Timer" aria-label="Pause Timer"><BsPauseFill /></button>
                          )}
                          <button className="timer-btn" onClick={() => setTimer(0)} title="Reset Timer" aria-label="Reset Timer"><BsArrowClockwise /></button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
              <div className="monaco-wrapper">
                <Editor
                  height="100%"
                  theme={globalTheme === 'light' ? 'light' : 'vs-dark'}
                  language={monacoLangFor(language)}
                  value={sourceCode}
                  onChange={(val) => setSourceCode(val)}
                  options={{ minimap: { enabled: false }, fontSize: 14, wordWrap: 'on' }}
                />
              </div>
            </div>

            <div className="results-container">
              <div className="panel-header tabs-header">
                <button className={`tab-btn ${activeTab === 'testcases' ? 'active' : ''}`} onClick={() => setActiveTab('testcases')}>Test Cases</button>
                <button className={`tab-btn ${activeTab === 'console' ? 'active' : ''}`} onClick={() => setActiveTab('console')}>Test Result</button>
              </div>
              <div className="panel-content results-content">
                {activeTab === 'testcases' && (
                  <div className="testcases-view">
                    {(!runResult || !runResult.testCaseResults || runResult.testCaseResults.length === 0) ? (
                      <p className="placeholder-text">Run your code to see test case results.</p>
                    ) : (
                      <div className="tc-layout">
                        <div className="tc-sidebar">
                          {runResult.testCaseResults.map((tc, idx) => (
                            <button 
                              key={idx}
                              className={`tc-pill ${activeTestCaseIndex === idx ? 'active' : ''} ${tc.passed ? 'tc-pass' : 'tc-fail'}`}
                              onClick={() => setActiveTestCaseIndex(idx)}
                            >
                              Case {idx + 1}
                            </button>
                          ))}
                        </div>
                        <div className="tc-details">
                          {runResult.testCaseResults[activeTestCaseIndex] && (
                            <>
                              <h4 className={runResult.testCaseResults[activeTestCaseIndex].passed ? 'text-success' : 'text-danger'}>
                                {runResult.testCaseResults[activeTestCaseIndex].passed ? 'Accepted' : 'Wrong Answer'}
                              </h4>
                              {(runResult.testCaseResults[activeTestCaseIndex].isHidden || runResult.testCaseResults[activeTestCaseIndex].hidden) ? (
                                <p className="hidden-warning">This is a hidden test case. Details are obscured.</p>
                              ) : (
                                <>
                                  <div className="io-box">
                                    <span className="io-label">Expected Output</span>
                                    <pre>{runResult.testCaseResults[activeTestCaseIndex].expectedOutput}</pre>
                                  </div>
                                  <div className="io-box">
                                    <span className="io-label">Your Output</span>
                                    <pre>{runResult.testCaseResults[activeTestCaseIndex].actualOutput}</pre>
                                  </div>
                                </>
                              )}
                            </>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                )}
                
                {activeTab === 'console' && (
                  <div className="console-view">
                    {!runResult ? (
                      <p className="placeholder-text">You must run your code first.</p>
                    ) : (
                      <div className="run-summary">
                        <h3 className={runResult.passed ? 'text-success' : 'text-danger'}>
                          {runResult.statusDescription || (runResult.passed ? 'Accepted' : 'Error')}
                        </h3>
                        {runResult.compileOutput && (
                          <div className="error-box">
                            <strong>Compilation/Runtime Error:</strong>
                            <pre>{runResult.compileOutput}</pre>
                          </div>
                        )}
                        <div className="metrics">
                          <span>⏱ Time: {runResult.executionTime || 0}s</span>
                          <span>💾 Memory: {runResult.memoryUsage || 0} KB</span>
                          <span>✅ Passed: {runResult.passedTests || 0}/{runResult.totalTests || 0}</span>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : (
          <div className="ide-right-panel evaluation-panel">
             <div className="panel-header">
                <h3>Final Evaluation</h3>
             </div>
             <div className="panel-content eval-content">
                <div className="score-hero">
                  <h1>{evaluation.finalScore}/100</h1>
                  <p>Overall Score</p>
                </div>
                
                <div className="eval-metrics">
                  <div className="metric-box">
                    <span>Quality</span>
                    <strong>{evaluation.codeQualityScore}</strong>
                  </div>
                  <div className="metric-box">
                    <span>Time Comp.</span>
                    <strong>{evaluation.timeComplexityScore}</strong>
                  </div>
                  <div className="metric-box">
                    <span>Space Comp.</span>
                    <strong>{evaluation.spaceComplexityScore}</strong>
                  </div>
                  <div className="metric-box">
                    <span>Style</span>
                    <strong>{evaluation.styleScore}</strong>
                  </div>
                </div>

                <div className="eval-test-cases" style={{ marginTop: '30px', padding: '20px', background: 'var(--surface)', borderRadius: '8px', border: '1px solid var(--border)' }}>
                  <h3 style={{ borderBottom: '1px solid var(--border)', paddingBottom: '10px', marginBottom: '15px' }}>
                    Passed {evaluation.passedTests || 0} / {evaluation.totalTests || 0} Test Cases
                  </h3>
                  <div className="tc-badges" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                    {Array.from({ length: evaluation.passedTests || 0 }).map((_, i) => (
                      <span key={`pass-${i}`} className="tc-badge pass" style={{ padding: '6px 12px', background: 'rgba(40, 167, 69, 0.1)', color: '#28a745', borderRadius: '16px', display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 'bold' }}>
                        <BsCheckCircleFill /> Passed Case
                      </span>
                    ))}
                    {Array.from({ length: evaluation.failedTests || 0 }).map((_, i) => (
                      <span key={`fail-${i}`} className="tc-badge fail" style={{ padding: '6px 12px', background: 'rgba(220, 53, 69, 0.1)', color: '#dc3545', borderRadius: '16px', display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 'bold' }}>
                        <BsXCircleFill /> Failed Case
                      </span>
                    ))}
                  </div>
                </div>

                <div className="eval-feedback" style={{ marginTop: '20px' }}>
                  <h4>Strengths</h4>
                  <p>{evaluation.strengths}</p>
                  
                  <h4>Weaknesses</h4>
                  <p>{evaluation.weaknesses}</p>
                  
                  <h4>Optimization Suggestions</h4>
                  <p>{evaluation.optimizationSuggestions}</p>
                </div>
                
                <div className="eval-actions" style={{ marginTop: '30px', textAlign: 'center' }}>
                  <button className="btn-run" onClick={() => window.location.href = `/interview/${question.interview?.id || question.interviewId || id}`} style={{ padding: '12px 24px', fontSize: '16px' }}>
                    Continue Interview
                  </button>
                </div>
             </div>
          </div>
        )}
      </div>

      {/* Mobile sticky End Interview action (mirrors the desktop top-right button) */}
      {!evaluation && (
        <button
          className="ide-mobile-endbtn"
          onClick={() => setShowEndModal(true)}
          disabled={ending}
        >
          {ending ? 'Ending…' : <><BsStopFill className="ide-end-icon" /> End Interview</>}
        </button>
      )}

      <ConfirmModal
        open={showEndModal}
        ending={ending}
        onCancel={() => setShowEndModal(false)}
        onConfirm={() => {
          setShowEndModal(false);
          handleEndInterview();
        }}
      />

      <ConfirmModal
        open={showSubmitModal}
        ending={submitting}
        title="Submit Solution?"
        message="Once submitted, your code will be evaluated and you won't be able to change it. Continue?"
        confirmLabel="Submit"
        cancelLabel="Cancel"
        icon={<BsCloudUploadFill />}
        onCancel={() => setShowSubmitModal(false)}
        onConfirm={() => {
          setShowSubmitModal(false);
          handleSubmit();
        }}
      />
    </div>
  );
};

export default CodingIDEPage;
