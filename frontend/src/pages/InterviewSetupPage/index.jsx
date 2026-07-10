import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  uploadResume,
  getResume,
  startInterview,
  getMurfVoices,
} from '../../services/interviewService.js';
import INTERVIEW_ROLES from '../../constants/roles.js';
import DIFFICULTY_LEVELS from '../../constants/difficulty.js';
import {
  BsDisplay,
  BsServer,
  BsLightningFill,
  BsGraphUp,
  BsCloudFill,
  BsStarFill,
  BsStar,
  BsFileEarmarkArrowUp,
  BsCheckCircleFill,
} from 'react-icons/bs';
import { FaPython, FaReact, FaJava, FaVolumeUp, FaVolumeMute } from 'react-icons/fa';
import toast from 'react-hot-toast';
import './index.css';

const ROLE_ICONS = {
  'frontend-developer': BsDisplay,
  'backend-developer': BsServer,
  'full-stack-developer': BsLightningFill,
  'data-analyst': BsGraphUp,
  'devops-engineer': BsCloudFill,
  'python-developer': FaPython,
  'react-developer': FaReact,
  'java-developer': FaJava,
};

const DIFFICULTY_ICONS = {
  starter: (
    <span className="setup-difficulty-stars">
      <BsStarFill className="setup-star-filled" />
      <BsStar className="setup-star-empty" />
      <BsStar className="setup-star-empty" />
    </span>
  ),
  standard: (
    <span className="setup-difficulty-stars">
      <BsStarFill className="setup-star-filled" />
      <BsStarFill className="setup-star-filled" />
      <BsStar className="setup-star-empty" />
    </span>
  ),
  advanced: (
    <span className="setup-difficulty-stars">
      <BsStarFill className="setup-star-filled" />
      <BsStarFill className="setup-star-filled" />
      <BsStarFill className="setup-star-filled" />
    </span>
  ),
};

function InterviewSetupPage() {
  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [selectedRole, setSelectedRole] = useState('');
  const [selectedDifficulty, setSelectedDifficulty] = useState('standard');
  const [resumeText, setResumeText] = useState('');
  const [parsedResume, setParsedResume] = useState(null);
  const [resumeId, setResumeId] = useState(null);
  const [resumeFileName, setResumeFileName] = useState('');
  
  const [voiceEnabled, setVoiceEnabled] = useState(true);
  const [voiceSpeed, setVoiceSpeed] = useState(1.0);
  const [murfVoices, setMurfVoices] = useState([]);
  const [selectedVoiceId, setSelectedVoiceId] = useState('');
  const [selectedStyle, setSelectedStyle] = useState('');
  const [voicesLoading, setVoicesLoading] = useState(false);

  // Fetch the list of Murf voices for the picker (only when voice is enabled)
  useEffect(() => {
    if (!voiceEnabled) return;
    let cancelled = false;
    const loadVoices = async () => {
      setVoicesLoading(true);
      try {
        const data = await getMurfVoices();
        if (!cancelled) setMurfVoices(data || []);
      } catch (e) {
        if (!cancelled) setMurfVoices([]);
      } finally {
        if (!cancelled) setVoicesLoading(false);
      }
    };
    loadVoices();
    return () => { cancelled = true; };
  }, [voiceEnabled]);
  const [loading, setLoading] = useState(false);
  const [uploadingResume, setUploadingResume] = useState(false);

    useEffect(() => {
    const loadResume = async () => {
      try {
        const data = await getResume();
        if (data) {
          setResumeText(data.text);
          setResumeFileName(data.fileName);
          setResumeId(data.id);
          if (data.structuredSkills) {
            try { setParsedResume(JSON.parse(data.structuredSkills)); } catch(e) {}
          }
        }
      } catch (error) {
        // No resume found - that's okay
      }
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
        try { setParsedResume(JSON.parse(data.structuredSkills)); } catch(e) {}
      }
      toast.success('Resume uploaded successfully!');
    } catch (error) {
      const message =
        error.response?.data?.message || 'Failed to upload resume';
      toast.error(message);
    } finally {
      setUploadingResume(false);
    }
  };

  const handleStartInterview = async () => {
    if (!selectedRole) {
      toast.error('Please select a role.');
      return;
    }
    if (!resumeText) {
      toast.error('Please upload your resume.');
      return;
    }

    setLoading(true);

    try {
      const data = await startInterview(
        selectedRole,
        resumeId,
        selectedDifficulty.toUpperCase(),
        {
          voiceEnabled,
          voiceId: selectedVoiceId || undefined,
          style: selectedStyle || undefined,
          voiceSpeed,
        }
      );
      toast.success('Interview started!');
      navigate(`/interview/${data.interviewId}`, {
        state: {
          audio: data.audio,
          voiceSettings: { voiceEnabled, voiceId: selectedVoiceId, voiceSpeed },
        },
      });
    } catch (error) {
      const message =
        error.response?.data?.message || 'Failed to start interview';
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  const handleNext = () => {
    if (step === 1 && !selectedRole) {
      toast.error('Please select a role.');
      return;
    }
    setStep((prev) => Math.min(prev + 1, 4));
  };

  const handleBack = () => {
    setStep((prev) => Math.max(prev - 1, 1));
  };

  if (loading) {
    return (
      <div className="setup-page">
        <div className="setup-preparing">
          <div className="spinner-border setup-preparing-spinner" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
          <h2 className="setup-preparing-heading">
            Preparing Your Interview...
          </h2>
          <p className="setup-preparing-text">
            AI is analyzing your resume and generating personalized questions for
            the <strong>{selectedRole}</strong> role.
          </p>
          <div className="setup-preparing-steps">
            <div className="setup-prep-step">
              <BsCheckCircleFill className="setup-prep-step-icon-active" />
              <span className="setup-prep-step-text">Analyzing resume</span>
            </div>
            <div className="setup-prep-step">
              <BsCheckCircleFill className="setup-prep-step-icon-active" />
              <span className="setup-prep-step-text">
                Generating questions
              </span>
            </div>
            <div className="setup-prep-step">
              <BsCheckCircleFill className="setup-prep-step-icon-pending" />
              <span className="setup-prep-step-text">
                Setting up voice interviewer
              </span>
            </div>
          </div>
          <p className="setup-preparing-hint">
            This may take 10-15 seconds...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="setup-page">
      <div className="setup-container">
        <div className="setup-step-indicator">
          <span
            className={`setup-step-badge ${step >= 1 ? 'setup-step-active' : ''}`}
          >
            1. Role
          </span>
          <span
            className={`setup-step-badge ${step >= 2 ? 'setup-step-active' : ''}`}
          >
            2. Difficulty
          </span>
          <span
            className={`setup-step-badge ${step >= 3 ? 'setup-step-active' : ''}`}
          >
            3. Resume
          </span>
          <span
            className={`setup-step-badge ${step >= 4 ? 'setup-step-active' : ''}`}
          >
            4. Voice Settings
          </span>
        </div>

        {step === 1 && (
          <div className="setup-section">
            <h2 className="setup-section-heading">Select Interview Role</h2>
            <div className="setup-roles-grid">
              {INTERVIEW_ROLES.map((role) => {
                const RoleIcon = ROLE_ICONS[role.id];
                return (
                  <button
                    key={role.id}
                    className={`setup-role-card ${selectedRole === role.title ? 'setup-role-selected' : ''}`}
                    onClick={() => setSelectedRole(role.title)}
                  >
                    {RoleIcon && <RoleIcon className="setup-role-icon" />}
                    <h3 className="setup-role-title">{role.title}</h3>
                    <p className="setup-role-desc">{role.description}</p>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {step === 2 && (
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

        {step === 3 && (
          <div className="setup-section">
            <h2 className="setup-section-heading">Upload Your Resume</h2>
            <div className="setup-resume-area">
              {resumeText ? (
                <div className="setup-resume-uploaded">
                  <div className="setup-resume-info">
                    <BsFileEarmarkArrowUp className="setup-resume-file-icon" />
                    <p className="setup-resume-name">{resumeFileName}</p>
                  </div>
                  <label className="setup-change-resume-btn">
                    Change
                    <input
                      type="file"
                      accept=".pdf"
                      onChange={handleResumeUpload}
                      hidden
                    />
                  </label>
                </div>
              ) : (
                <label className="setup-upload-zone">
                  <BsFileEarmarkArrowUp className="setup-upload-icon" />
                  <p className="setup-upload-text">
                    {uploadingResume
                      ? 'Uploading...'
                      : 'Click to upload PDF resume'}
                  </p>
                  <input
                    type="file"
                    accept=".pdf"
                    onChange={handleResumeUpload}
                    disabled={uploadingResume}
                    hidden
                  />
                </label>
              )}
            </div>
          </div>
        )}

        {step === 4 && (
          <div className="setup-section">
            <h2 className="setup-section-heading">Voice Interviewer Settings</h2>

            <div className="setup-field">
              <label className="setup-label">Enable voice interviewer</label>
              <label className="setup-toggle">
                <input
                  type="checkbox"
                  checked={voiceEnabled}
                  onChange={(e) => setVoiceEnabled(e.target.checked)}
                />
                <span>{voiceEnabled ? 'On' : 'Off'}</span>
              </label>
            </div>

            {voiceEnabled && (
              <>
                <div className="setup-field">
                  <label className="setup-label">Interviewer Voice</label>
                  {voicesLoading ? (
                    <p className="setup-voice-hint">Loading voices…</p>
                  ) : murfVoices.length === 0 ? (
                    <p className="setup-voice-hint">
                      Voice list unavailable (Murf not configured). A default voice will be used.
                    </p>
                  ) : (
                    <select
                      className="setup-input"
                      value={selectedVoiceId}
                      onChange={(e) => {
                        const v = murfVoices.find((x) => x.voiceId === e.target.value);
                        setSelectedVoiceId(e.target.value);
                        setSelectedStyle(v && v.styles && v.styles.length ? v.styles[0] : '');
                      }}
                    >
                      <option value="">Default voice</option>
                      {murfVoices.map((v) => (
                        <option key={v.voiceId} value={v.voiceId}>
                          {v.name} ({v.gender || '—'}, {v.language || v.locale || 'en'})
                        </option>
                      ))}
                    </select>
                  )}
                </div>

                {(() => {
                  const current = murfVoices.find((x) => x.voiceId === selectedVoiceId);
                  if (!current || !current.styles || current.styles.length === 0) return null;
                  return (
                    <div className="setup-field">
                      <label className="setup-label">Speaking Style</label>
                      <select
                        className="setup-input"
                        value={selectedStyle}
                        onChange={(e) => setSelectedStyle(e.target.value)}
                      >
                        {current.styles.map((s) => (
                          <option key={s} value={s}>{s}</option>
                        ))}
                      </select>
                    </div>
                  );
                })()}

                <div className="setup-field">
                  <label className="setup-label">
                    Speaking Speed: {voiceSpeed.toFixed(2)}x
                  </label>
                  <input
                    type="range"
                    min="0.5"
                    max="2"
                    step="0.1"
                    value={voiceSpeed}
                    onChange={(e) => setVoiceSpeed(parseFloat(e.target.value))}
                    className="setup-range"
                  />
                </div>
              </>
            )}
          </div>
        )}

        <div className="setup-nav-buttons">
          {step > 1 && (
            <button className="setup-back-btn" onClick={handleBack}>
              Back
            </button>
          )}
          {step < 4 ? (
            <button className="setup-next-btn" onClick={handleNext}>
              Next
            </button>
          ) : (
            <button
              className={`setup-start-btn ${loading || !selectedRole || !resumeText ? 'setup-start-btn-disabled' : ''}`}
              onClick={handleStartInterview}
              disabled={loading || !selectedRole || !resumeText}
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
