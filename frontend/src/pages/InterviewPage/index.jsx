import { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import {
  getInterview,
  submitTextAnswer,
  submitVoiceAnswer,
  transcribeAudio,
  submitCode,
  runCode,
  endInterview,
  getWelcomeIntroduction,
  generateSpeech,
} from '../../services/interviewService.js';
import VoiceRecorder from '../../components/VoiceRecorder';
import AudioPlayer from '../../components/AudioPlayer';
import CodeEditor from '../../components/CodeEditor';
import { FaUserTie } from 'react-icons/fa';
import {
  BsRecordCircleFill,
  BsKeyboardFill,
  BsCodeSlash,
  BsCheck,
  BsCheckCircleFill,
  BsXCircleFill,
} from 'react-icons/bs';
import toast from 'react-hot-toast';
import './index.css';

const STATE_SPEAKING = 'speaking';
const STATE_THINKING = 'thinking';
const STATE_LISTENING = 'listening';
const STATE_FAREWELL = 'farewell';

function InterviewPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [ending, setEnding] = useState(false);
  const [isRecording, setIsRecording] = useState(false);

  const [interviewerState, setInterviewerState] = useState(STATE_SPEAKING);

  const [showTextFallback, setShowTextFallback] = useState(false);
  const [textAnswer, setTextAnswer] = useState('');

  const [code, setCode] = useState('');
  const [codeLanguage, setCodeLanguage] = useState('javascript');
  const [codeEvaluation, setCodeEvaluation] = useState(null);
  const [runCodeResult, setRunCodeResult] = useState(null);
  const [runningCode, setRunningCode] = useState(false);

  const [currentAudio, setCurrentAudio] = useState(null);
  const [audioKey, setAudioKey] = useState(0);

  const [currentQuestionNum, setCurrentQuestionNum] = useState(1);
  const [targetQuestions, setTargetQuestions] = useState(15);
  const [totalQuestions, setTotalQuestions] = useState(5);
  const [currentQuestion, setCurrentQuestion] = useState(null);
  const [interviewerText, setInterviewerText] = useState('');
  const [farewellMessage, setFarewellMessage] = useState('');
  
  const [questionStartTime, setQuestionStartTime] = useState(null);

  useEffect(() => {
    return () => {
      // Force halt any lingering audio when leaving the interview page
      import('../../services/VoiceQueueService').then(m => m.default.stopAll());
    };
  }, []);

  useEffect(() => {
    const loadInterview = async () => {
      try {
        const data = await getInterview(id);
        setCurrentQuestionNum(data.currentQuestion);
        setTotalQuestions(data.totalQuestions);
        // FIX: Initialize targetQuestions from interview data, not hardcoded 15
        if (data.targetQuestions) setTargetQuestions(data.targetQuestions);
        else if (data.totalQuestions) setTargetQuestions(data.totalQuestions);

        if (data.questions && data.questions.length > 0) {
          const qIndex = data.currentQuestion - 1;
          setCurrentQuestion(data.questions[qIndex] || data.questions[0]);
        }

        // FIX: guard against null/undefined messages list
        const messages = Array.isArray(data.messages) ? data.messages : [];
        const interviewerMsgs = messages.filter(
          (m) => m.role === 'interviewer'
        );
        if (data.currentQuestion === 1 && interviewerMsgs.length >= 1) {
          setInterviewerText(interviewerMsgs[0].content);
        } else if (interviewerMsgs.length > 0) {
          setInterviewerText(interviewerMsgs[interviewerMsgs.length - 1].content);
        }

        if (data.currentQuestion === 1) {
          const passedAudio = location.state?.audio || data.lastAudio;
          if (passedAudio) {
            // Audio came from startInterview response — play immediately
            setCurrentAudio(passedAudio);
            setInterviewerState(STATE_SPEAKING);
          } else {
            // Fetch dynamic welcome introduction from backend
            setInterviewerState(STATE_SPEAKING);
            try {
              const welcome = await getWelcomeIntroduction(id);
              if (welcome?.text) setInterviewerText(welcome.text);
              if (welcome?.audio) {
                setCurrentAudio(welcome.audio);
                setAudioKey((prev) => prev + 1);
              } else {
                // No Murf audio available — advance after short delay
                setTimeout(() => {
                  setInterviewerState(STATE_LISTENING);
                  setQuestionStartTime(Date.now());
                }, 4000);
              }
            } catch (_) {
              setTimeout(() => {
                setInterviewerState(STATE_LISTENING);
                setQuestionStartTime(Date.now());
              }, 3000);
            }
          }
        } else {
          setInterviewerState(STATE_LISTENING);
          setQuestionStartTime(Date.now());
        }
      } catch (error) {
        toast.error('Failed to load interview');
        navigate('/');
      } finally {
        setLoading(false);
      }
    };
    loadInterview();
  }, [id, navigate, location.state]);

  const handleAudioEnded = () => {
    if (interviewerState === STATE_FAREWELL) return;
    setInterviewerState(STATE_LISTENING);
    setQuestionStartTime(Date.now());
  };

  const resetAnswerFields = () => {
    setTextAnswer('');
    setCode('');
    setCodeEvaluation(null);
    setRunCodeResult(null);
    setShowTextFallback(false);
  };

  const processAnswerResult = (result) => {
    // FIX: Backend field is serialized as "isComplete" via @JsonProperty.
    // Support both for robustness.
    const isComplete = result.isComplete === true || result.complete === true;

    if (isComplete) {
      const farewellText =
        'Thank you for completing the interview! I really enjoyed our conversation. Let me prepare your detailed feedback report...';
      setFarewellMessage(farewellText);
      setInterviewerState(STATE_FAREWELL);

      if (result.audio) {
        setTimeout(() => {
          setCurrentAudio(result.audio);
          setAudioKey((prev) => prev + 1);
        }, 100);
        // Navigate after audio plays or after a generous delay
        setTimeout(() => handleEndInterview(), 10000);
      } else {
        // FIX: endInterview was already called server-side on last answer;
        // just navigate to feedback page
        setTimeout(() => navigate(`/feedback/${id}`), 3000);
      }
      return;
    }

    setInterviewerText(result.response);
    setCurrentQuestionNum(result.currentQuestion);
    if (result.targetQuestions) setTargetQuestions(result.targetQuestions);
    setCurrentQuestion(result.question);
    setCurrentAudio(result.audio);
    setAudioKey((prev) => prev + 1);
    resetAnswerFields();

    setInterviewerState(STATE_SPEAKING);
    if (!result.audio) {
      // Voice fallback: try to generate TTS client-side when backend audio is null
      if (result.question?.text) {
        generateSpeech(id, (result.response || '') + ' ' + result.question.text)
          .then((audioUrl) => {
            if (audioUrl) {
              setCurrentAudio(audioUrl);
              setAudioKey((prev) => prev + 1);
            } else {
              setTimeout(() => {
                setInterviewerState(STATE_LISTENING);
                setQuestionStartTime(Date.now());
              }, 3000);
            }
          })
          .catch(() => {
            setTimeout(() => {
              setInterviewerState(STATE_LISTENING);
              setQuestionStartTime(Date.now());
            }, 3000);
          });
      } else {
        setTimeout(() => {
          setInterviewerState(STATE_LISTENING);
          setQuestionStartTime(Date.now());
        }, 3000);
      }
    }
  };

  const submitAndProcess = async (answerText) => {
    setSubmitting(true);
    setInterviewerState(STATE_THINKING);
    const responseTimeSeconds = questionStartTime ? Math.round((Date.now() - questionStartTime) / 1000) : 60;
    try {
      const result = await submitTextAnswer(id, answerText, responseTimeSeconds);
      processAnswerResult(result);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to submit answer');
      setInterviewerState(STATE_LISTENING);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRecordingComplete = async (audioBlob) => {
    setSubmitting(true);
    setInterviewerState(STATE_THINKING);
    const responseTimeSeconds = questionStartTime ? Math.round((Date.now() - questionStartTime) / 1000) : 60;
    try {
      const result = await submitVoiceAnswer(id, audioBlob, responseTimeSeconds);
      processAnswerResult(result);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to submit audio answer');
      setInterviewerState(STATE_LISTENING);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmitText = () => {
    if (!textAnswer.trim()) return toast.error('Please type your answer.');
    submitAndProcess(textAnswer);
  };

  const handleRunCode = async () => {
    if (!code.trim()) return toast.error('Please write some code to run.');
    if (currentQuestion?.codeLanguage && currentQuestion.codeLanguage.toLowerCase() !== codeLanguage.toLowerCase()) {
      return toast.error(`Please use ${currentQuestion.codeLanguage} for this question.`);
    }

    setRunningCode(true);
    setRunCodeResult(null);
    try {
      const result = await runCode(id, code, codeLanguage);
      setRunCodeResult(result);
      if (result.error) {
        toast.error(result.error);
      } else if (result.passed) {
        toast.success(`✅ Sample tests passed: ${result.passedTests}/${result.totalTests}`);
      } else {
        toast(`⚠️ ${result.passedTests ?? 0}/${result.totalTests ?? 0} sample tests passed`);
      }
    } catch (error) {
      toast.error('Failed to run code');
    } finally {
      setRunningCode(false);
    }
  };


  const handleSubmitCode = async () => {
    if (!code.trim()) return toast.error('Please write some code.');
    if (currentQuestion?.codeLanguage && currentQuestion.codeLanguage.toLowerCase() !== codeLanguage.toLowerCase()) {
      return toast.error(`Please use ${currentQuestion.codeLanguage} for this question.`);
    }

    setSubmitting(true);
    setInterviewerState(STATE_THINKING);
    const responseTimeSeconds = questionStartTime ? Math.round((Date.now() - questionStartTime) / 1000) : 60;
    try {
      const result = await submitCode(id, code, codeLanguage, responseTimeSeconds);

      // FIX: same isComplete robustness as processAnswerResult
      const isComplete = result.isComplete === true || result.complete === true;

      if (result.evaluation) {
        setCodeEvaluation(result.evaluation);
        toast.success(`Code evaluated: ${result.evaluation.score}/100`);
      }

      if (isComplete) {
        setFarewellMessage(
          'Thank you for completing the interview! I really enjoyed our conversation. Let me prepare your detailed feedback report...'
        );
        setInterviewerState(STATE_FAREWELL);
        if (result.audio) {
          setTimeout(() => {
            setCurrentAudio(result.audio);
            setAudioKey((prev) => prev + 1);
          }, 100);
          setTimeout(() => navigate(`/feedback/${id}`), 10000);
        } else {
          // endInterview already called server-side; just navigate
          setTimeout(() => navigate(`/feedback/${id}`), 3000);
        }
        return;
      }

      setTimeout(() => processAnswerResult(result), 2500);
    } catch (error) {
      toast.error('Failed to evaluate code');
      setInterviewerState(STATE_LISTENING);
    } finally {
      setSubmitting(false);
    }
  };

  const handleEndInterview = async () => {
    setEnding(true);
    try {
      // endInterview generates & saves the feedback report
      await endInterview(id);
      // Navigate only after the API call succeeds (feedback is saved)
      navigate(`/feedback/${id}`);
    } catch (error) {
      // If endInterview fails (e.g. already completed), still try to navigate
      // because feedback may already exist
      console.warn('endInterview error (may already be completed):', error);
      navigate(`/feedback/${id}`);
    } finally {
      setEnding(false);
    }
  };
  if (loading) {
    return (
      <div className="interview-loading-state">
        <div className="spinner-border spinner-border-sm" role="status" />
        <p className="interview-loading-text">Loading interview...</p>
      </div>
    );
  }

  const isCodeQuestion = currentQuestion?.isCodeQuestion;
  const progressPercent = (currentQuestionNum / targetQuestions) * 100;
  const isSpeaking = interviewerState === STATE_SPEAKING;
  const isThinking = interviewerState === STATE_THINKING;
  const isListening = interviewerState === STATE_LISTENING;
  const isFarewell = interviewerState === STATE_FAREWELL;

  return (
    <div className="interview-layout">
      <div className="interview-topbar">
        <div className="topbar-left">
          <span className="topbar-question-label">
            Question {currentQuestionNum} / {targetQuestions}
          </span>
          <div className="topbar-progress-track">
            <div
              className="topbar-progress-fill"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>
        <div className="topbar-right">
          {currentQuestionNum >= targetQuestions && isListening && (
            <button
              className={`topbar-end-btn ${ending ? 'topbar-end-btn-disabled' : ''}`}
              onClick={handleEndInterview}
              disabled={ending}
            >
              {ending ? 'Generating Feedback...' : 'End Interview'}
            </button>
          )}
        </div>
      </div>

      <div className="interviewer-panel">
        <div className="interviewer-avatar-block">
          <div className="interviewer-avatar-circle">
            <FaUserTie className="interviewer-avatar-icon" />
          </div>
          <div className="interviewer-avatar-info">
            <span className="interviewer-avatar-name">Natalie</span>
            <span className="interviewer-avatar-role">AI Interviewer</span>
          </div>
        </div>

        <div className="interviewer-status-block">
          {isSpeaking && (
            <span className="status-text status-speaking">Speaking...</span>
          )}
          {isThinking && (
            <div className="status-thinking-row">
              <div className="spinner-border spinner-border-sm" role="status" />
              <span className="status-text status-thinking">Thinking...</span>
            </div>
          )}
          {isListening && (
            <div className="status-listening-row">
              <BsRecordCircleFill className="status-listening-icon" />
              <span className="status-text status-listening">
                Your turn to answer
              </span>
            </div>
          )}
          {isFarewell && (
            <div className="status-farewell-row">
              <div className="spinner-border spinner-border-sm" role="status" />
              <span className="status-text status-farewell">
                Wrapping up...
              </span>
            </div>
          )}
        </div>

        {currentAudio && !isRecording && (
          <AudioPlayer
            key={audioKey}
            audioSrc={currentAudio}
            autoPlay={true}
            onEnded={handleAudioEnded}
            audioText={currentQuestion?.text || interviewerText}
          />
        )}

        {isFarewell && (
          <div className="interviewer-farewell-block">
            <p className="interviewer-farewell-text">{farewellMessage}</p>
            <div className="spinner-border spinner-border-sm" role="status" />
          </div>
        )}

        {!isFarewell && !isThinking && interviewerText && (
          <div className="interviewer-message-block">
            <p className="interviewer-message-text">{interviewerText}</p>
          </div>
        )}

        {!isFarewell && currentQuestion && !isThinking && (
          <div className="interviewer-question-callout">
            <div className="question-callout-header">
              <span className="question-num-badge">Q{currentQuestionNum}</span>
              <span className="question-type-badge">{currentQuestion.type}</span>
              {isCodeQuestion && (
                <span className="question-code-badge">
                  <BsCodeSlash className="question-code-icon" /> Code
                </span>
              )}
              {isCodeQuestion && currentQuestion.tags && (
                <span className="question-tags-badge">{currentQuestion.tags}</span>
              )}
            </div>

            {/* LeetCode-style coding question display */}
            {isCodeQuestion ? (
              <div className="coding-problem-panel">
                {currentQuestion.title && (
                  <h3 className="coding-problem-title">{currentQuestion.title}</h3>
                )}
                <p className="coding-problem-description">
                  {currentQuestion.problemDescription || currentQuestion.text}
                </p>
                {(currentQuestion.exampleInput || currentQuestion.exampleOutput) && (
                  <div className="coding-problem-example">
                    <h4 className="coding-example-label">Example</h4>
                    {currentQuestion.exampleInput && (
                      <div className="coding-example-row">
                        <span className="coding-example-key">Input:</span>
                        <code className="coding-example-val">{currentQuestion.exampleInput}</code>
                      </div>
                    )}
                    {currentQuestion.exampleOutput && (
                      <div className="coding-example-row">
                        <span className="coding-example-key">Output:</span>
                        <code className="coding-example-val">{currentQuestion.exampleOutput}</code>
                      </div>
                    )}
                  </div>
                )}
                {currentQuestion.constraints && (
                  <div className="coding-problem-constraints">
                    <h4 className="coding-constraints-label">Constraints</h4>
                    <pre className="coding-constraints-text">{currentQuestion.constraints}</pre>
                  </div>
                )}
                {currentQuestion.timeComplexity && (
                  <div className="coding-problem-hint">
                    <span className="coding-hint-label">Expected:</span>
                    <code className="coding-hint-val">{currentQuestion.timeComplexity}</code>
                  </div>
                )}
              </div>
            ) : (
              <p className="question-callout-text">{currentQuestion.text}</p>
            )}
          </div>
        )}

      </div>

      <div className="answer-panel">
        {isListening && (
          <>
            {!isCodeQuestion && (
              <>
                <div className="voice-answer-block">
                  <div className="voice-block-header">
                    <div>
                      <h3 className="voice-block-title">Record Your Answer</h3>
                      <p className="voice-block-desc">
                        Click record, speak your answer (max 5 min), then submit
                      </p>
                    </div>
                  </div>
                  <div className="voice-block-area">
                    {!submitting && (
                      <VoiceRecorder
                        onRecordingComplete={handleRecordingComplete}
                        onRecordingStart={() => setIsRecording(true)}
                        onRecordingStop={() => setIsRecording(false)}
                        disabled={submitting || isSpeaking}
                        autoStart={true}
                      />
                    )}
                    {submitting && (
                      <div className="processing-indicator">
                        <div
                          className="spinner-border spinner-border-sm"
                          role="status"
                        />
                        <p className="processing-text">
                          Processing your answer...
                        </p>
                      </div>
                    )}
                  </div>
                </div>

                <div className="text-fallback-block">
                  <button
                    className="text-fallback-toggle-btn"
                    onClick={() => setShowTextFallback(!showTextFallback)}
                  >
                    <span className="text-fallback-toggle-label">
                      <BsKeyboardFill className="text-fallback-icon" />
                      {showTextFallback
                        ? 'Hide text input'
                        : 'Prefer typing instead?'}
                    </span>
                    <span
                      className={
                        showTextFallback
                          ? 'toggle-arrow-open'
                          : 'toggle-arrow-closed'
                      }
                    >
                      &#9660;
                    </span>
                  </button>
                  {showTextFallback && (
                    <div className="text-answer-block">
                      <textarea
                        className={`text-answer-textarea ${submitting ? 'text-answer-textarea-disabled' : ''}`}
                        placeholder="Type your answer here..."
                        value={textAnswer}
                        onChange={(e) => setTextAnswer(e.target.value)}
                        rows={4}
                        disabled={submitting}
                      />
                      <button
                        className={`submit-text-btn ${submitting || !textAnswer.trim() ? 'submit-text-btn-disabled' : ''}`}
                        onClick={handleSubmitText}
                        disabled={submitting || !textAnswer.trim()}
                      >
                        {submitting ? 'Submitting...' : 'Submit Text Answer'}
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}

            {isCodeQuestion && (
              <div className="code-answer-block">
                <div className="code-block-header">
                  <h3 className="code-block-title">
                    <BsCodeSlash className="code-title-icon" />
                    {currentQuestion.codeType === 'fix'
                      ? 'Fix the Code'
                      : currentQuestion.codeType === 'explain'
                      ? 'Explain the Code'
                      : 'Write Your Solution'}
                  </h3>
                  <select
                    value={codeLanguage}
                    onChange={(e) => setCodeLanguage(e.target.value)}
                    className="code-language-select"
                  >
                    <option value="javascript">JavaScript</option>
                    <option value="python">Python</option>
                    <option value="java">Java</option>
                    <option value="cpp">C++</option>
                  </select>
                </div>

                {currentQuestion.codeSnippet && (
                  <div className="code-snippet-box">
                    <h4 className="code-snippet-label">
                      {currentQuestion.codeType === 'fix'
                        ? 'Buggy Code:'
                        : 'Code to Explain:'}
                    </h4>
                    <pre className="code-snippet-pre">
                      {currentQuestion.codeSnippet}
                    </pre>
                  </div>
                )}

                <CodeEditor
                  value={
                    code ||
                    currentQuestion.starterCode ||
                    currentQuestion.codeSnippet ||
                    ''
                  }
                  onChange={(val) => setCode(val || '')}
                  language={currentQuestion.codeLanguage || codeLanguage}
                />
                <div className="code-action-buttons">
                  <button
                    className={`run-code-btn ${runningCode || !code.trim() ? 'run-code-btn-disabled' : ''}`}
                    onClick={handleRunCode}
                    disabled={runningCode || submitting || !code.trim()}
                  >
                    {runningCode ? '▶ Running...' : '▶ Run Code'}
                  </button>
                  <button
                    className={`submit-code-btn ${submitting || !code.trim() ? 'submit-code-btn-disabled' : ''}`}
                    onClick={handleSubmitCode}
                    disabled={submitting || runningCode || !code.trim()}
                  >
                    {submitting
                      ? 'Evaluating...'
                      : currentQuestion.codeType === 'fix'
                      ? 'Submit Fixed Code'
                      : 'Submit Solution'}
                  </button>
                </div>

                {/* Phase 7: Test Results Panel */}
                {runCodeResult && (
                  <div className={`code-results-panel ${runCodeResult.passed ? 'code-results-pass' : 'code-results-fail'}`}>
                    <div className="code-results-header">
                      {runCodeResult.error ? (
                        <span className="code-results-status-fail">⚠ Execution Unavailable</span>
                      ) : runCodeResult.passed ? (
                        <span className="code-results-status-pass">✅ All Sample Tests Passed ({runCodeResult.passedTests}/{runCodeResult.totalTests})</span>
                      ) : (
                        <span className="code-results-status-fail">❌ {runCodeResult.passedTests ?? 0}/{runCodeResult.totalTests ?? 0} Sample Tests Passed</span>
                      )}
                      {runCodeResult.executionTime > 0 && (
                        <span className="code-results-meta">
                          ⏱ {runCodeResult.executionTime}s · 💾 {runCodeResult.memoryUsage} KB
                        </span>
                      )}
                    </div>
                    {runCodeResult.compileOutput && (
                      <pre className="code-results-output code-results-error">
                        <strong>Compile Error:</strong>{'\n'}{runCodeResult.compileOutput}
                      </pre>
                    )}
                    {runCodeResult.stdout && (
                      <pre className="code-results-output">
                        <strong>Output:</strong>{'\n'}{runCodeResult.stdout}
                      </pre>
                    )}
                    {runCodeResult.stderr && (
                      <pre className="code-results-output code-results-error">
                        <strong>Error:</strong>{'\n'}{runCodeResult.stderr}
                      </pre>
                    )}
                    {runCodeResult.note && (
                      <p className="code-results-note">{runCodeResult.note}</p>
                    )}
                  </div>
                )}

                {codeEvaluation && (
                  <div
                    className={
                      codeEvaluation.isCorrect
                        ? 'code-eval-block code-eval-correct'
                        : 'code-eval-block code-eval-incorrect'
                    }
                  >
                    <div className="code-eval-header">
                      <span className="code-eval-status">
                        {codeEvaluation.isCorrect ? (
                          <>
                            <BsCheckCircleFill className="code-eval-icon-correct" />
                            Correct
                          </>
                        ) : (
                          <>
                            <BsXCircleFill className="code-eval-icon-incorrect" />
                            Needs Improvement
                          </>
                        )}
                      </span>
                      <span className="code-eval-score">
                        Score: {codeEvaluation.score}/100
                      </span>
                    </div>
                    <p className="code-eval-feedback">
                      {codeEvaluation.feedback}
                    </p>
                    {codeEvaluation.suggestions && (
                      <p className="code-eval-suggestions">
                        <strong>Tip:</strong> {codeEvaluation.suggestions}
                      </p>
                    )}
                  </div>
                )}
              </div>
            )}
          </>
        )}

        {isSpeaking && (
          <div className="answer-panel-status">
            <p className="answer-panel-status-text">
              Natalie is speaking... please listen carefully
            </p>
          </div>
        )}
        {isThinking && (
          <div className="answer-panel-status">
            <div className="spinner-border spinner-border-sm" role="status" />
            <p className="answer-panel-status-text">
              Natalie is preparing the next question...
            </p>
          </div>
        )}
        {isFarewell && (
          <div className="answer-panel-status">
            <div className="spinner-border spinner-border-sm" role="status" />
            <p className="answer-panel-status-text">
              Generating your feedback report...
            </p>
          </div>
        )}
      </div>

      <div className="interview-timeline">
        <div className="timeline-dots-row">
          {Array.from({ length: targetQuestions }, (_, i) => {
            const qNum = i + 1;
            const isAnswered = qNum < currentQuestionNum;
            const isCurrent = qNum === currentQuestionNum;
            let dotClass = 'timeline-dot-circle';
            if (isAnswered) dotClass += ' timeline-dot-answered';
            if (isCurrent) dotClass += ' timeline-dot-current';
            return (
              <div key={i} className={dotClass}>
                {isAnswered ? (
                  <BsCheck className="timeline-check-icon" />
                ) : (
                  qNum
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default InterviewPage;
