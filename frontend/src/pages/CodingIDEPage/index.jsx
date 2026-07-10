import React, { useState, useEffect } from 'react';
import { useParams, useLocation } from 'react-router-dom';
import axios from 'axios';
import Editor from '@monaco-editor/react';
import { BsPlayFill, BsCloudUploadFill, BsArrowsAngleExpand, BsArrowsAngleContract, BsCheckCircleFill, BsXCircleFill } from 'react-icons/bs';
import { useTheme } from '../../hooks/useTheme.js';
import './CodingIDEPage.css';

const CodingIDEPage = () => {
  const { id } = useParams();
  const location = useLocation();

  const [question, setQuestion] = useState(null);
  const [loading, setLoading] = useState(true);
  
  const { theme: globalTheme } = useTheme();
  const [language, setLanguage] = useState('java');
  const [monacoThemeOverride, setMonacoThemeOverride] = useState('');
  const [sourceCode, setSourceCode] = useState('');
  
  const [isFullscreen, setIsFullscreen] = useState(false);
  
  const [submitting, setSubmitting] = useState(false);
  const [running, setRunning] = useState(false);
  
  const [runResult, setRunResult] = useState(null);
  const [activeTab, setActiveTab] = useState('console'); // 'console' | 'testcases'
  const [activeTestCaseIndex, setActiveTestCaseIndex] = useState(0);

  const [evaluation, setEvaluation] = useState(null);
  const [timer, setTimer] = useState(0);

  const initialized = React.useRef(false);

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
        
        const langSupport = q.languageSupport ? q.languageSupport.split(',') : ['java'];
        const initialLang = langSupport[0];
        setLanguage(initialLang);
        
        if (q.starterCode) {
          const starterMap = JSON.parse(q.starterCode);
          setSourceCode(starterMap[initialLang] || '');
        }
      } catch (err) {
        console.error('Failed to load coding interview.', err);
      } finally {
        setLoading(false);
      }
    };
    startModule();
  }, [id]);

  useEffect(() => {
    let interval;
    if (!loading && !evaluation) {
      interval = setInterval(() => {
        setTimer(t => t + 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [loading, evaluation]);

  const handleLanguageChange = (e) => {
    const newLang = e.target.value;
    setLanguage(newLang);
    if (question && question.starterCode) {
      try {
        const starterMap = JSON.parse(question.starterCode);
        setSourceCode(starterMap[newLang] || '');
      } catch (e) {
        // Fallback if parsing fails
      }
    }
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
      setRunResult({
        compileOutput: err.response?.data?.message || err.message,
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
      setRunResult({
        compileOutput: 'Error submitting code: ' + (err.response?.data?.message || err.message),
        passed: false
      });
      setActiveTab('console');
    } finally {
      setSubmitting(false);
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
    <div className={`leetcode-ide ${isFullscreen ? 'ide-fullscreen' : ''}`}>
      <div className="ide-navbar">
        <div className="navbar-left">
          <span className="ide-logo">IDE</span>
          <span className="timer-badge">⏱ {formatTime(timer)}</span>
        </div>
        
        <div className="navbar-center">
          <div className="action-buttons">
            <button className="btn-run" onClick={handleRun} disabled={running || submitting || evaluation}>
              {running ? 'Running...' : <><BsPlayFill /> Run Code</>}
            </button>
            <button className="btn-submit" onClick={handleSubmit} disabled={submitting || running || evaluation}>
              {submitting ? 'Submitting...' : <><BsCloudUploadFill /> Submit</>}
            </button>
          </div>
        </div>

        <div className="navbar-right">
          <button className="btn-icon" onClick={() => setIsFullscreen(!isFullscreen)}>
            {isFullscreen ? <BsArrowsAngleContract /> : <BsArrowsAngleExpand />}
          </button>
        </div>
      </div>

      <div className="ide-body">
        {/* Left Panel: Problem Description */}
        <div className="ide-left-panel">
          <div className="panel-header">
            <h3>Description</h3>
          </div>
          <div className="panel-content problem-description">
            <h1 className="problem-title">{question.title}</h1>
            <div className={`difficulty-badge diff-${question.difficulty?.toLowerCase()}`}>
              {question.difficulty}
            </div>
            
            <div className="problem-text">
              <p>{question.description}</p>
            </div>
            
            {question.constraints && (
              <div className="problem-constraints">
                <strong>Constraints:</strong>
                <pre>{question.constraints}</pre>
              </div>
            )}
          </div>
        </div>

        {/* Right Panel: Editor and Results */}
        {!evaluation ? (
          <div className="ide-right-panel">
            <div className="editor-container">
              <div className="panel-header editor-header">
                <select className="ide-select" value={language} onChange={handleLanguageChange} disabled={running || submitting}>
                  {(question.languageSupport || 'java').split(',').map(l => (
                    <option key={l} value={l}>{l.toUpperCase()}</option>
                  ))}
                </select>
                <select className="ide-select" value={monacoThemeOverride || (globalTheme === 'light' ? 'light' : 'vs-dark')} onChange={e => setMonacoThemeOverride(e.target.value)}>
                  <option value="vs-dark">VS Dark</option>
                  <option value="light">Light</option>
                  <option value="hc-black">High Contrast</option>
                </select>
              </div>
              <div className="monaco-wrapper">
                <Editor
                  height="100%"
                  theme={monacoThemeOverride || (globalTheme === 'light' ? 'light' : 'vs-dark')}
                  language={language === 'c++' ? 'cpp' : language}
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
                              {runResult.testCaseResults[activeTestCaseIndex].isHidden ? (
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

                <div className="eval-feedback">
                  <h4>Strengths</h4>
                  <p>{evaluation.strengths}</p>
                  
                  <h4>Weaknesses</h4>
                  <p>{evaluation.weaknesses}</p>
                  
                  <h4>Optimization Suggestions</h4>
                  <p>{evaluation.optimizationSuggestions}</p>
                </div>
             </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CodingIDEPage;
