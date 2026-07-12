import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  uploadResume,
  getResume,
  startInterview,
} from '../../services/interviewService.js';
import DIFFICULTY_LEVELS from '../../constants/difficulty.js';
import {
  MODES,
  getRolesForMode,
  getResumePolicy,
} from '../../constants/interviewModes.js';
import {
  BsStarFill,
  BsStar,
  BsFileEarmarkArrowUp,
  BsCheckCircleFill,
  BsVolumeUp,
  BsShieldCheck,
} from 'react-icons/bs';
import toast from 'react-hot-toast';
import './index.css';

// Map role ids to an icon for the (read-only) auto-detected resume card and
// the summary line. Falls back to a generic icon.
const ROLE_FALLBACK_ICON = {
  RESUME_AUTO: BsFileEarmarkArrowUp,
};

const DIFFICULTY_ICONS = {
  BEGINNER: (
    <span className="setup-difficulty-stars">
      <BsStarFill className="setup-star-filled" />
      <BsStar className="setup-star-empty" />
      <BsStar className="setup-star-empty" />
    </span>
  ),
  INTERMEDIATE: (
    <span className="setup-difficulty-stars">
      <BsStarFill className="setup-star-filled" />
      <BsStarFill className="setup-star-filled" />
      <BsStar className="setup-star-empty" />
    </span>
  ),
  ADVANCED: (
    <span className="setup-difficulty-stars">
      <BsStarFill className="setup-star-filled" />
      <BsStarFill className="setup-star-filled" />
      <BsStarFill className="setup-star-filled" />
    </span>
  ),
};

// Step definitions. The "resume" step is only present for modes that surface
// the upload control (everything except CODING, where it is hidden).
const STEP_NAMES = {
  mode: 'Mode',
  resume: 'Resume',
  difficulty: 'Difficulty',
  preferences: 'Preferences',
  start: 'Ready',
};

function buildSteps(mode) {
  const steps = [{ id: 'mode' }];
  const policy = getResumePolicy(mode);
  if (!policy.hidden) steps.push({ id: 'resume' });
  steps.push({ id: 'difficulty' }, { id: 'preferences' }, { id: 'start' });
  return steps.map((s, i) => ({ ...s, label: `${i + 1}. ${STEP_NAMES[s.id]}` }));
}

function InterviewSetupPage() {
  const navigate = useNavigate();

  const [stepIndex, setStepIndex] = useState(0);
  const [selectedRole, setSelectedRole] = useState('');

  const [interviewMode, setInterviewMode] = useState(''); // canonical mode id
  const [targetCount, setTargetCount] = useState(15);
  const [selectedDifficulty, setSelectedDifficulty] = useState('INTERMEDIATE');

  const [resumeText, setResumeText] = useState('');
  const [parsedResume, setParsedResume] = useState(null);
  const [resumeId, setResumeId] = useState(null);
  const [resumeFileName, setResumeFileName] = useState('');

  const [loading, setLoading] = useState(false);
  const [uploadingResume, setUploadingResume] = useState(false);

  // Build the step list from the selected mode and keep the cursor in range.
  const steps = useMemo(() => buildSteps(interviewMode), [interviewMode]);
  useEffect(() => {
    setStepIndex((idx) => Math.min(idx, steps.length - 1));
  }, [steps.length]);

  // When the mode changes away from a role that no longer applies, reset the role.
  useEffect(() => {
    if (!interviewMode) {
      setSelectedRole('');
      return;
    }
    const valid = getRolesForMode(interviewMode).some((r) => r.id === selectedRole);
    if (!valid && interviewMode !== 'RESUME_BASED') {
      setSelectedRole('');
    }
  }, [interviewMode, selectedRole]);

  // Auto role for resume-based interviews (not user-selectable).
  const effectiveRole = useMemo(() => {
    if (interviewMode === 'RESUME_BASED') {
      const detected =
        parsedResume?.title ||
        parsedResume?.role ||
        parsedResume?.headline;
      return detected || 'Resume-Based Candidate';
    }
    return selectedRole;
  }, [interviewMode, parsedResume, selectedRole]);

  const resumePolicy = getResumePolicy(interviewMode);



  useEffect(() => {
    const loadResume = async () => {
      try {
        const data = await getResume();
        if (data) {
          setResumeText(data.text);
          setResumeFileName(data.fileName);
          setResumeId(data.id);
          if (data.structuredSkills) {
            try { setParsedResume(JSON.parse(data.structuredSkills)); } catch (e) {}
          }
        }
      } catch (error) {}
    };
    loadResume();
  }, []);

  const handleResumeUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (file.type !== 'application/pdf') {
      toast.error('Please upload a PDF file.');
      return;
    }

    setUploadingResume(true);
    try {
      const data = await uploadResume(file);
      setResumeText(data.text);
      setResumeFileName(data.fileName);
      setResumeId(data.id);
      if (data.structuredSkills) {
        try { setParsedResume(JSON.parse(data.structuredSkills)); } catch (e) {}
      }
      toast.success('Resume uploaded successfully!');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to upload resume');
    } finally {
      setUploadingResume(false);
    }
  };

  const currentStep = steps[stepIndex];

  const handleNext = () => {
    if (currentStep.id === 'mode' && !interviewMode) {
      toast.error('Please select an interview mode.');
      return;
    }
    if (currentStep.id === 'resume') {
      if (resumePolicy.required && !resumeText) {
        toast.error('Please upload your resume to continue.');
        return;
      }
      // Optional modes: resume is not required.
    }
    if (currentStep.id === 'preferences' && interviewMode !== 'RESUME_BASED' && !selectedRole) {
      toast.error('Please select a target role.');
      return;
    }
    setStepIndex((prev) => Math.min(prev + 1, steps.length - 1));
  };

  const handleBack = () => {
    setStepIndex((prev) => Math.max(prev - 1, 0));
  };

  const canStart = useMemo(() => {
    if (loading) return false;
    if (interviewMode !== 'RESUME_BASED' && !selectedRole) return false;
    if (resumePolicy.required && !resumeText) return false;
    return true;
  }, [loading, interviewMode, selectedRole, resumePolicy, resumeText]);

  const handleStartInterview = async () => {
    if (!canStart) return;
    setLoading(true);
    try {
      const data = await startInterview(
        effectiveRole,
        resumeId,
        selectedDifficulty.toUpperCase(),
        {
          voiceEnabled: interviewMode !== 'CODING',
          voiceId: undefined,
          style: undefined,
          voiceSpeed: 1.0,
          interviewMode,
          mode: interviewMode,
          // Programming language is now chosen inside the Coding IDE.
          targetQuestionCount: targetCount,
        }
      );
      toast.success('Interview started!');
      if (interviewMode === 'CODING') {
        navigate(`/coding-module/${data.interviewId}`);
      } else {
        navigate(`/interview/${data.interviewId}`, {
          state: {
            audio: data.audio,
            voiceSettings: { voiceEnabled: true, voiceId: undefined, voiceSpeed: 1.0 },
          },
        });
      }
    } catch (error) {
      const respData = error.response?.data;
      if (respData && respData.fallbackUsed && respData.fallbackData) {
        const data = respData.fallbackData;
        if (interviewMode === 'CODING') {
          navigate(`/coding-module/${data.interviewId}`, { state: { aiError: respData } });
        } else {
          navigate(`/interview/${data.interviewId}`, {
            state: {
              audio: data.audio,
              voiceSettings: { voiceEnabled: interviewMode !== 'CODING', voiceId: undefined, voiceSpeed: 1.0 },
              aiError: respData,
            },
          });
        }
      } else {
        toast.error(respData?.message || 'Failed to start interview');
      }
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="setup-page">
        <div className="setup-container">
          <div className="setup-preparing">
            <div className="spinner-border setup-preparing-spinner" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
            <h2 className="setup-preparing-heading">Preparing Your Interview...</h2>
            <p className="setup-preparing-text">
              {interviewMode === 'RESUME_BASED'
                ? 'AI is analyzing your resume and generating personalized questions.'
                : 'AI is generating your questions.'}
            </p>
            <div className="setup-preparing-steps">
              <div className="setup-prep-step">
                <BsCheckCircleFill className="setup-prep-step-icon-active" />
                <span className="setup-prep-step-text">Analyzing requirements</span>
              </div>
              <div className="setup-prep-step">
                <BsCheckCircleFill className="setup-prep-step-icon-active" />
                <span className="setup-prep-step-text">Generating questions</span>
              </div>
              {interviewMode !== 'CODING' && (
                <div className="setup-prep-step">
                  <BsCheckCircleFill className="setup-prep-step-icon-pending" />
                  <span className="setup-prep-step-text">Setting up voice interviewer</span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }

  const roles = interviewMode ? getRolesForMode(interviewMode) : [];

  return (
    <div className="setup-page">
      <div className="setup-container">
        <div className="setup-step-indicator">
          {steps.map((s, idx) => (
            <span key={s.id} className={`setup-step-badge ${idx <= stepIndex ? 'setup-step-active' : ''}`}>
              {s.label}
            </span>
          ))}
        </div>

        <div className="setup-step-content">
          {/* ───────────────────────── MODE ───────────────────────── */}
          {currentStep.id === 'mode' && (
            <div className="setup-section">
              <h2 className="setup-section-heading">Choose Interview Mode</h2>
              <div className="setup-mode-grid" role="radiogroup" aria-label="Interview mode">
                {MODES.map((m) => {
                  const Icon = m.icon;
                  const selected = interviewMode === m.id;
                  return (
                    <button
                      key={m.id}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      aria-label={`Select ${m.title} interview mode`}
                      tabIndex={0}
                      className={`setup-mode-card ${selected ? 'setup-mode-selected' : ''}`}
                      style={selected ? { '--mode-accent': m.accent } : undefined}
                      onClick={() => setInterviewMode(m.id)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          setInterviewMode(m.id);
                        }
                      }}
                    >
                      <span className="setup-mode-icon" style={{ color: m.accent }}>
                        <Icon />
                      </span>
                      <span className="setup-mode-title">{m.title}</span>
                      <span className="setup-mode-desc">{m.description}</span>

                      <span className="setup-mode-meta">
                        {m.questionCount && (
                          <span className="setup-mode-meta-item">{m.questionCount}</span>
                        )}
                        {m.difficulty && (
                          <span className="setup-mode-meta-item">{m.difficulty}</span>
                        )}
                        {m.interviewType && (
                          <span className="setup-mode-meta-item">{m.interviewType}</span>
                        )}
                      </span>

                      <span className="setup-mode-select">
                        {selected ? 'Selected' : 'Select Mode'}
                      </span>

                      {selected && (
                        <span className="setup-mode-check">
                          <BsCheckCircleFill />
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>

              <div className="setup-field" style={{ marginTop: '32px' }}>
                <label className="setup-label">Interview Length</label>
                <div className="setup-difficulty-row">
                  <button className={`setup-difficulty-card ${targetCount === 10 ? 'setup-difficulty-selected' : ''}`} onClick={() => setTargetCount(10)}>
                    <h3 className="setup-difficulty-label">Short</h3>
                    <p className="setup-difficulty-desc">10 Questions</p>
                  </button>
                  <button className={`setup-difficulty-card ${targetCount === 15 ? 'setup-difficulty-selected' : ''}`} onClick={() => setTargetCount(15)}>
                    <h3 className="setup-difficulty-label">Medium</h3>
                    <p className="setup-difficulty-desc">15 Questions</p>
                  </button>
                  <button className={`setup-difficulty-card ${targetCount === 20 ? 'setup-difficulty-selected' : ''}`} onClick={() => setTargetCount(20)}>
                    <h3 className="setup-difficulty-label">Long</h3>
                    <p className="setup-difficulty-desc">20 Questions</p>
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ───────────────────────── RESUME ─────────────────────── */}
          {currentStep.id === 'resume' && (
            <div className="setup-section">
              <h2 className="setup-section-heading">
                {resumePolicy.required ? 'Upload Your Resume (Required)' : 'Upload Your Resume (Optional)'}
              </h2>
              <div className="setup-resume-area">
                {resumeText ? (
                  <div className="setup-resume-uploaded">
                    <div className="setup-resume-info">
                      <BsFileEarmarkArrowUp className="setup-resume-file-icon" />
                      <p className="setup-resume-name">{resumeFileName}</p>
                    </div>
                    <label className="setup-change-resume-btn">
                      Change
                      <input type="file" accept=".pdf" onChange={handleResumeUpload} hidden />
                    </label>
                  </div>
                ) : (
                  <label className={`setup-upload-zone ${resumePolicy.required ? 'setup-upload-zone-required' : ''}`}>
                    <BsFileEarmarkArrowUp className="setup-upload-icon" />
                    <p className="setup-upload-text">
                      {uploadingResume ? 'Uploading...' : 'Click to upload PDF resume'}
                    </p>
                    <input type="file" accept=".pdf" onChange={handleResumeUpload} disabled={uploadingResume} hidden />
                  </label>
                )}
              </div>
              {resumePolicy.helper && (
                <p
                  className={`setup-resume-helper ${resumePolicy.required ? 'setup-resume-helper-required' : ''}`}
                >
                  {resumePolicy.required && <BsShieldCheck className="setup-resume-helper-icon" />}
                  {resumePolicy.helper}
                </p>
              )}
              {resumePolicy.required && !resumeText && (
                <p className="setup-resume-error">Start Interview is disabled until a resume is uploaded.</p>
              )}
            </div>
          )}

          {/* ───────────────────────── DIFFICULTY ─────────────────── */}
          {currentStep.id === 'difficulty' && (
            <div className="setup-section">
              <h2 className="setup-section-heading">Choose Difficulty</h2>
              <div className="setup-difficulty-row">
                {DIFFICULTY_LEVELS.map((level) => (
                  <button
                    key={level.id}
                    className={`setup-difficulty-card ${selectedDifficulty === level.id ? 'setup-difficulty-selected' : ''}`}
                    onClick={() => setSelectedDifficulty(level.id)}
                  >
                    {DIFFICULTY_ICONS[level.id]}
                    <h3 className="setup-difficulty-label">{level.label}</h3>
                    <p className="setup-difficulty-desc">{level.description}</p>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* ───────────────────────── PREFERENCES ────────────────── */}
          {currentStep.id === 'preferences' && (
            <div className="setup-section">
              <h2 className="setup-section-heading">Interview Preferences</h2>

              <div className="setup-field" style={{ marginBottom: '32px' }}>
                <label className="setup-label">
                  {interviewMode === 'RESUME_BASED' ? 'Target Role (Auto-detected)' : 'Target Role (Required)'}
                </label>
                {interviewMode === 'RESUME_BASED' ? (
                  <div className="setup-roles-grid">
                    {roles.map((r) => {
                      const Icon = r.icon || ROLE_FALLBACK_ICON[r.id] || BsShieldCheck;
                      return (
                        <div key={r.id} className="setup-role-card setup-role-selected setup-role-readonly" aria-disabled="true">
                          <span className="setup-role-icon"><Icon /></span>
                          <p className="setup-role-title">{r.title}</p>
                          <p className="setup-role-desc">{r.description}</p>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <div className="setup-roles-grid" role="radiogroup" aria-label="Target role">
                    {roles.map((r) => {
                      const Icon = r.icon;
                      const selected = selectedRole === r.id;
                      return (
                        <button
                          key={r.id}
                          type="button"
                          role="radio"
                          aria-checked={selected}
                          aria-label={`Select ${r.title} role`}
                          tabIndex={0}
                          className={`setup-role-card ${selected ? 'setup-role-selected' : ''}`}
                          onClick={() => setSelectedRole(r.id)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault();
                              setSelectedRole(r.id);
                            }
                          }}
                        >
                          <span className="setup-role-icon"><Icon /></span>
                          <p className="setup-role-title">{r.title}</p>
                          <p className="setup-role-desc">{r.description}</p>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>


            </div>
          )}

          {/* ───────────────────────── START ──────────────────────── */}
          {currentStep.id === 'start' && (
            <div className="setup-section" style={{ textAlign: 'center' }}>
              <h2 className="setup-section-heading">Ready to begin!</h2>
              <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem' }}>
                You have selected the <strong>{effectiveRole}</strong> role with a <strong>{selectedDifficulty}</strong> difficulty level
                {interviewMode ? ` in a <strong>${interviewMode.replace('_', ' ').toLowerCase()}</strong> interview` : ''}.
              </p>
              <BsCheckCircleFill style={{ fontSize: '4rem', color: 'var(--success-color)', marginBottom: '2rem' }} />
              <p>Click "Start Interview" when you are ready.</p>
            </div>
          )}
        </div>

        <div className="setup-nav-buttons">
          <button
            className="setup-back-btn"
            onClick={handleBack}
            style={{ visibility: stepIndex === 0 ? 'hidden' : 'visible' }}
          >
            Back
          </button>

          {stepIndex < steps.length - 1 ? (
            <button className="setup-next-btn" onClick={handleNext}>
              Next
            </button>
          ) : (
            <button
              className={`setup-start-btn ${!canStart ? 'setup-start-btn-disabled' : ''}`}
              onClick={handleStartInterview}
              disabled={!canStart}
            >
              Start Interview
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default InterviewSetupPage;
