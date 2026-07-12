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
import { useNotification } from '../../components/NotificationProvider';
import { mapAiErrorToNotification } from '../../components/GlobalNotification';
import ConfirmModal from '../../components/ConfirmModal';
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
  const [showEndModal, setShowEndModal] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isAudioPlaying, setIsAudioPlaying] = useState(false);
  const [forceStopRecording, setForceStopRecording] = useState(false);

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
  
  const { showNotification } = useNotification();
  const [questionStartTime, setQuestionStartTime] = useState(null);

  // Captured interview mode (from the API) so we can enforce CODING-only questions.
  const [interviewMode, setInterviewMode] = useState(null);

  // When coding question changes, seed the language + starter code from the question
  useEffect(() => {
    if (currentQuestion?.isCodeQuestion) {
      // Build supported langs list (trimmed, lowercase)
      const langs = currentQuestion.languageSupport
        ? currentQuestion.languageSupport.split(',').map(s => s.trim().toLowerCase())
        : ['javascript'];

      // Pick the question's preferred language, or keep current if still valid
      let pickedLang = langs[0];
      if (langs.includes(codeLanguage.toLowerCase())) {
        pickedLang = codeLanguage.toLowerCase();
      }
      setCodeLanguage(pickedLang);

      // Immediately seed editor with the correct starter code
      const starter = getStarterCodeForLang(currentQuestion, pickedLang);
      setCode(starter);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentQuestion]);

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
          const q = data.questions[qIndex] || data.questions[0];
          setCurrentQuestion(q);
          setInterviewMode(data.interviewMode || null);
          validateCodingQuestion(q, data.interviewMode);
        } else {
          setInterviewMode(data.interviewMode || null);
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

  // Surface an AI error passed in via navigation state as a global notification.
  useEffect(() => {
    if (location.state?.aiError) {
      showNotification(mapAiErrorToNotification(location.state.aiError));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * Resolve starter code for a given question + language.
   * Handles both JSON-map format (CodingModule flow) and plain-string format.
   * Language keys are normalized to lowercase for lookup.
   */
  const getStarterCodeForLang = (question, lang) => {
    if (!question) return '';
    const normalizedLang = (lang || 'javascript').toLowerCase().trim();

    // 1. Try the JSON starterCode map first (both exact and normalized key)
    if (question.starterCode) {
      try {
        const parsed = JSON.parse(question.starterCode);
        if (parsed && typeof parsed === 'object') {
          // Try exact key, then lowercase key, then 'cpp'/'c++' aliases
          const aliases = buildLangAliases(normalizedLang);
          for (const alias of aliases) {
            if (parsed[alias] != null) return parsed[alias];
          }
        }
      } catch (_) {
        // starterCode is a plain string (interview-flow single-language)
        return question.starterCode;
      }
    }

    // 2. Fall back to codeSnippet field
    if (question.codeSnippet) return question.codeSnippet;

    // 3. Generate a canonical empty stub for the language
    return defaultStub(normalizedLang);
  };

  /** Alias list: e.g. 'c++' <-> 'cpp', 'js' <-> 'javascript' */
  const buildLangAliases = (lang) => {
    const map = {
      'c++':        ['c++', 'cpp'],
      'cpp':        ['cpp', 'c++'],
      'js':         ['js', 'javascript'],
      'javascript': ['javascript', 'js'],
      'ts':         ['ts', 'typescript'],
      'typescript': ['typescript', 'ts'],
      'py':         ['py', 'python'],
      'python':     ['python', 'py'],
    };
    return map[lang] || [lang];
  };

  /** Canonical empty method stub shown when no starterCode exists for a language */
  const defaultStub = (lang) => {
    switch (lang) {
      case 'java':       return 'public int solve(int[] input) {\n    // Write your code here\n}';
      case 'python':     return 'def solve(input):\n    pass';
      case 'javascript': return 'function solve(input) {\n    // Write your code here\n}';
      case 'c++':        return '#include<bits/stdc++.h>\nusing namespace std;\n\nint solve(vector<int>& input) {\n    // Write your code here\n    return 0;\n}';
      case 'cpp':        return '#include<bits/stdc++.h>\nusing namespace std;\n\nint solve(vector<int>& input) {\n    // Write your code here\n    return 0;\n}';
      case 'c':          return '#include<stdio.h>\n\nint solve(int* input) {\n    /* Write your code here */\n    return 0;\n}';
      case 'typescript': return 'function solve(input: number[]): number {\n    // Write your code here\n    return 0;\n}';
      case 'go':         return 'func solve(input []int) int {\n    // Write your code here\n    return 0\n}';
      default:           return `// Write your ${lang} solution here\n`;
    }
  };

  // Keep legacy alias for any existing callers
  const getStarterCode = getStarterCodeForLang;

  /**
   * CODING-mode integrity check. A CODING interview must ONLY ever show coding
   * questions. If the backend returns a non-coding question for a CODING interview
   * (which should be impossible after the backend fix), surface a hard error instead
   * of silently presenting an MCQ / behavioral question.
   */
  const validateCodingQuestion = (q, mode) => {
    const m = mode || interviewMode;
    if (m === 'CODING' && q && q.type && q.type !== 'coding' && !q.isCodeQuestion) {
      const msg = 'Invalid question type received. This coding interview received a non-coding question.';
      showNotification(mapAiErrorToNotification({ message: msg, provider: 'Validation', status: 500 }));
    }
  };


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
    validateCodingQuestion(result.question, interviewMode);
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
      const respData = error.response?.data;
      if (respData && respData.fallbackUsed && respData.fallbackData) {
        showNotification(mapAiErrorToNotification(respData));
        processAnswerResult(respData.fallbackData);
      } else {
        toast.error(respData?.message || 'Failed to submit answer');
        setInterviewerState(STATE_LISTENING);
      }
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
      const respData = error.response?.data;
      if (respData && respData.fallbackUsed && respData.fallbackData) {
        showNotification(mapAiErrorToNotification(respData));
        processAnswerResult(respData.fallbackData);
      } else {
        toast.error(respData?.message || 'Failed to submit audio answer');
        setInterviewerState(STATE_LISTENING);
      }
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
    if (currentQuestion?.languageSupport) {
      const langs = currentQuestion.languageSupport.split(',').map(s => s.trim().toLowerCase());
      if (!langs.includes(codeLanguage.toLowerCase())) {
        return toast.error(`Please select a supported language: ${currentQuestion.languageSupport}`);
      }
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
      const msg = error.response?.data?.message || error.message || 'Failed to run code';
      toast.error(`Execution failed: ${msg}`);
      setRunCodeResult({ error: msg });
    } finally {
      setRunningCode(false);
    }
  };


  const handleSubmitCode = async () => {
    if (!code.trim()) return toast.error('Please write some code.');
    if (currentQuestion?.languageSupport) {
      const langs = currentQuestion.languageSupport.split(',').map(s => s.trim().toLowerCase());
      if (!langs.includes(codeLanguage.toLowerCase())) {
        return toast.error(`Please select a supported language: ${currentQuestion.languageSupport}`);
      }
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
      const respData = error.response?.data;
      if (respData && respData.fallbackUsed && respData.fallbackData) {
        showNotification(mapAiErrorToNotification(respData));
        const fbData = respData.fallbackData;
        
        if (fbData.evaluation) {
          setCodeEvaluation(fbData.evaluation);
          toast.success(`Code evaluated: ${fbData.evaluation.score}/100`);
        }
        
        const isComplete = fbData.isComplete === true || fbData.complete === true;
        if (isComplete) {
          setFarewellMessage(
            'Thank you for completing the interview! I really enjoyed our conversation. Let me prepare your detailed feedback report...'
          );
          setInterviewerState(STATE_FAREWELL);
          if (fbData.audio) {
            setTimeout(() => {
              setCurrentAudio(fbData.audio);
              setAudioKey((prev) => prev + 1);
            }, 100);
            setTimeout(() => navigate(`/feedback/${id}`), 10000);
          } else {
            setTimeout(() => navigate(`/feedback/${id}`), 3000);
          }
          return;
        }

        setTimeout(() => processAnswerResult(fbData), 2500);
      } else {
        toast.error('Failed to evaluate code');
        setInterviewerState(STATE_LISTENING);
      }
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
          {/* Persistent End Interview — visible on every question */}
          <button
            className={`topbar-end-btn ${ending ? 'topbar-end-btn-disabled' : ''}`}
            onClick={() => setShowEndModal(true)}
            disabled={ending}
          >
            {ending ? 'Ending…' : 'End Interview'}
          </button>
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

        {currentAudio && (
          <AudioPlayer
            key={audioKey}
            audioSrc={currentAudio}
            autoPlay={true}
            onEnded={handleAudioEnded}
            onPlayStart={() => setIsAudioPlaying(true)}
            onPlayEnd={() => setIsAudioPlaying(false)}
            onReplay={() => {
              if (isRecording) {
                setForceStopRecording(true);
                setTimeout(() => setForceStopRecording(false), 500);
              }
            }}
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
                {/* Test Cases Panel — shows visible test cases from AI; hidden ones are never sent */}
                {currentQuestion.testCases && currentQuestion.testCases.length > 0 ? (
                  <div className="coding-problem-example">
                    <h4 className="coding-example-label">Examples</h4>
                    {currentQuestion.testCases.map((tc, idx) => (
                      <div key={idx} className="coding-example-case" style={{ marginBottom: '14px', padding: '10px', background: 'var(--bg-lighter, rgba(255,255,255,0.05))', borderRadius: '6px', border: '1px solid var(--border)' }}>
                        <strong style={{ display: 'block', marginBottom: '6px', color: 'var(--text-primary)', fontSize: '13px' }}>
                          {tc.name || `Example ${idx + 1}`}
                        </strong>
                        <div className="coding-example-row">
                          <span className="coding-example-key">Input:</span>
                          <code className="coding-example-val">{tc.input}</code>
                        </div>
                        <div className="coding-example-row">
                          <span className="coding-example-key">Output:</span>
                          <code className="coding-example-val">{tc.expectedOutput}</code>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (currentQuestion.exampleInput || currentQuestion.exampleOutput) ? (
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
                ) : Boolean(currentQuestion.isCodeQuestion) && (
                  <div className="coding-problem-example">
                    <h4 className="coding-example-label">Examples</h4>
                    <p style={{ color: 'var(--text-secondary)', fontStyle: 'italic', margin: 0, fontSize: '13px' }}>No test cases available.</p>
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
                        disabled={submitting || isAudioPlaying || showTextFallback}
                        autoStart={true}
                        forceStop={forceStopRecording}
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
                    onChange={(e) => {
                      const newLang = e.target.value.toLowerCase().trim();
                      setCodeLanguage(newLang);
                      // Immediately load the correct template for the new language
                      const starter = getStarterCodeForLang(currentQuestion, newLang);
                      setCode(starter);
                    }}
                    className="code-language-select"
                  >
                    {(currentQuestion.languageSupport
                      ? currentQuestion.languageSupport.split(',').map(s => s.trim().toLowerCase())
                      : ['javascript', 'python', 'java', 'cpp']
                    ).map(lang => (
                      <option key={lang} value={lang}>
                        {lang === 'javascript' ? 'JavaScript'
                          : lang === 'typescript' ? 'TypeScript'
                          : lang === 'python' ? 'Python'
                          : lang === 'java' ? 'Java'
                          : lang === 'c++' || lang === 'cpp' ? 'C++'
                          : lang === 'c' ? 'C'
                          : lang === 'go' ? 'Go'
                          : lang.charAt(0).toUpperCase() + lang.slice(1)}
                      </option>
                    ))}
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
                  value={code || getStarterCodeForLang(currentQuestion, codeLanguage)}
                  onChange={(val) => setCode(val || '')}
                  language={codeLanguage}
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
                {runningCode ? (
                  <div className="code-results-panel code-results-loading">
                    <div className="code-results-header">
                      <span className="code-results-status-loading">⏳ Executing code on remote server...</span>
                    </div>
                  </div>
                ) : runCodeResult && (
                  <div className={`code-results-panel ${runCodeResult.passed ? 'code-results-pass' : 'code-results-fail'}`}>
                    <div className="code-results-header">
                      {runCodeResult.error ? (
                        <span className="code-results-status-fail">⚠ {runCodeResult.error}</span>
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
                    {runCodeResult.error && !runCodeResult.compileOutput && (
                      <pre className="code-results-output code-results-error">
                        <strong>Error Details:</strong>{'\n'}{runCodeResult.error}
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
                    {codeEvaluation.totalTests > 0 && (
                      <div className="code-eval-metrics" style={{ marginTop: '1rem', padding: '10px', backgroundColor: 'var(--bg-lighter)', borderRadius: '6px' }}>
                        <h4 style={{ margin: '0 0 10px 0', fontSize: '14px', color: 'var(--text-primary)' }}>Execution Metrics</h4>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', fontSize: '13px' }}>
                          <div><strong>Passed Cases:</strong> {codeEvaluation.passedTests}</div>
                          <div><strong>Failed Cases:</strong> {codeEvaluation.totalTests - codeEvaluation.passedTests}</div>
                          <div><strong>Execution Time:</strong> {codeEvaluation.executionTime ? codeEvaluation.executionTime + 's' : 'N/A'}</div>
                          <div><strong>Memory Usage:</strong> {codeEvaluation.memoryUsage ? codeEvaluation.memoryUsage + ' KB' : 'N/A'}</div>
                        </div>
                      </div>
                    )}
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

      {/* Mobile sticky End Interview action (mirrors the desktop top-right button) */}
      <button
        className="interview-mobile-endbtn"
        onClick={() => setShowEndModal(true)}
        disabled={ending}
      >
        {ending ? 'Ending…' : 'End Interview'}
      </button>

      <ConfirmModal
        open={showEndModal}
        ending={ending}
        onCancel={() => setShowEndModal(false)}
        onConfirm={() => {
          setShowEndModal(false);
          handleEndInterview();
        }}
      />
    </div>
  );
}

export default InterviewPage;
