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
import { FaPython, FaReact, FaJava, FaVolumeUp } from 'react-icons/fa';
import toast from 'react-hot-toast';
import CustomDropdown from '../../components/CustomDropdown';
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

function InterviewSetupPage() {
  const navigate = useNavigate();

  const [stepIndex, setStepIndex] = useState(0);
  const [selectedRole, setSelectedRole] = useState('');
  
  const [interviewMode, setInterviewMode] = useState('RESUME');
  const [targetCount, setTargetCount] = useState(15);
  const [codingLanguage, setCodingLanguage] = useState('javascript');
  const [selectedInterests, setSelectedInterests] = useState([]);

  const AVAILABLE_INTERESTS = ['Java', 'Spring Boot', 'MERN', 'AI/ML', 'Data Science', 'Cloud', 'DevOps', 'System Design'];

  const toggleInterest = (interest) => {
    setSelectedInterests(prev => 
      prev.includes(interest) ? prev.filter(i => i !== interest) : [...prev, interest]
    );
  };

  const [selectedDifficulty, setSelectedDifficulty] = useState('INTERMEDIATE');
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
        try { setParsedResume(JSON.parse(data.structuredSkills)); } catch(e) {}
      }
      toast.success('Resume uploaded successfully!');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to upload resume');
    } finally {
      setUploadingResume(false);
    }
  };

  const getSteps = () => {
    const s = [
      { id: 'mode', label: '1. Mode' },
      { id: 'resume', label: '2. Resume' },
      { id: 'difficulty', label: '3. Difficulty' },
      { id: 'preferences', label: '4. Preferences' },
      { id: 'start', label: '5. Ready' }
    ];
    return s;
  };

  const steps = getSteps();
  const currentStep = steps[stepIndex];

  const handleNext = () => {
    if (currentStep.id === 'mode' && !interviewMode) {
      toast.error('Please select an interview mode.');
      return;
    }
    if (currentStep.id === 'resume' && !resumeText) {
      toast.error('Please upload your resume.');
      return;
    }
    if (currentStep.id === 'preferences' && !selectedRole) {
      toast.error('Please select a target role.');
      return;
    }
    setStepIndex((prev) => Math.min(prev + 1, steps.length - 1));
  };

  const handleBack = () => {
    setStepIndex((prev) => Math.max(prev - 1, 0));
  };

  const handleStartInterview = async () => {
    setLoading(true);
    try {
      const data = await startInterview(
        selectedRole,
        resumeId,
        selectedDifficulty.toUpperCase(),
        {
          voiceEnabled: interviewMode !== 'CODING_INTERVIEW' && voiceEnabled,
          voiceId: selectedVoiceId || undefined,
          style: selectedStyle || undefined,
          voiceSpeed,
          interviewMode,
          codingLanguage: interviewMode === 'CODING_INTERVIEW' ? codingLanguage : undefined,
          selectedInterests: (interviewMode === 'INTEREST_BASED' || interviewMode === 'HYBRID') ? selectedInterests.join(', ') : undefined,
          targetQuestionCount: targetCount,
        }
      );
      toast.success('Interview started!');
      if (interviewMode === 'CODING_INTERVIEW') {
        navigate(`/coding-module/${data.interviewId}`);
      } else {
        navigate(`/interview/${data.interviewId}`, {
          state: {
            audio: data.audio,
            voiceSettings: { voiceEnabled, voiceId: selectedVoiceId, voiceSpeed },
          },
        });
      }
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to start interview');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="setup-page">
        <div className="setup-preparing">
          <div className="spinner-border setup-preparing-spinner" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
          <h2 className="setup-preparing-heading">Preparing Your Interview...</h2>
          <p className="setup-preparing-text">
            AI is analyzing your resume and generating personalized questions.
          </p>
          <div className="setup-preparing-steps">
            <div className="setup-prep-step">
              <BsCheckCircleFill className="setup-prep-step-icon-active" />
              <span className="setup-prep-step-text">Analyzing resume</span>
            </div>
            <div className="setup-prep-step">
              <BsCheckCircleFill className="setup-prep-step-icon-active" />
              <span className="setup-prep-step-text">Generating questions</span>
            </div>
            {interviewMode !== 'CODING_INTERVIEW' && (
              <div className="setup-prep-step">
                <BsCheckCircleFill className="setup-prep-step-icon-pending" />
                <span className="setup-prep-step-text">Setting up voice interviewer</span>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  const interviewModeOptions = [
    { value: 'RESUME', label: 'Resume-Based' },
    { value: 'TECHNICAL', label: 'Technical Deep Dive' },
    { value: 'HR', label: 'Behavioral / HR' },
    { value: 'PROJECT', label: 'Project-Based' },
    { value: 'CODING_INTERVIEW', label: 'Coding Interview' },
    { value: 'INTEREST_BASED', label: 'Interest-Based' },
    { value: 'HYBRID', label: 'Hybrid (All-in-One)' },
  ];

  const languageOptions = [
    { value: 'javascript', label: 'JavaScript / Node.js' },
    { value: 'python', label: 'Python' },
    { value: 'java', label: 'Java' },
    { value: 'cpp', label: 'C++' },
    { value: 'sql', label: 'SQL' },
  ];

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
          {currentStep.id === 'resume' && (
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
                      <input type="file" accept=".pdf" onChange={handleResumeUpload} hidden />
                    </label>
                  </div>
                ) : (
                  <label className="setup-upload-zone">
                    <BsFileEarmarkArrowUp className="setup-upload-icon" />
                    <p className="setup-upload-text">
                      {uploadingResume ? 'Uploading...' : 'Click to upload PDF resume'}
                    </p>
                    <input type="file" accept=".pdf" onChange={handleResumeUpload} disabled={uploadingResume} hidden />
                  </label>
                )}
              </div>
            </div>
          )}

          {currentStep.id === 'mode' && (
            <div className="setup-section">
              <h2 className="setup-section-heading">Interview Mode</h2>
              <div className="setup-field">
                <label className="setup-label">Interview Mode</label>
                <CustomDropdown
                  options={interviewModeOptions}
                  value={interviewMode}
                  onChange={(val) => setInterviewMode(val)}
                  searchable={false}
                />
              </div>
              
              <div className="setup-field" style={{ marginTop: '20px' }}>
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

              {(interviewMode === 'INTEREST_BASED' || interviewMode === 'HYBRID') && (
                <div className="setup-field" style={{ marginTop: '20px' }}>
                  <label className="setup-label">Select Specific Interests</label>
                  <div className="setup-interest-tags">
                    {AVAILABLE_INTERESTS.map(interest => (
                      <button
                        key={interest}
                        type="button"
                        className={`setup-interest-tag ${selectedInterests.includes(interest) ? 'setup-interest-tag-active' : ''}`}
                        onClick={() => toggleInterest(interest)}
                      >
                        {interest}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

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

          {currentStep.id === 'preferences' && (
            <div className="setup-section">
              <h2 className="setup-section-heading">Interview Preferences</h2>
              
              <div className="setup-field" style={{ marginBottom: '32px' }}>
                <label className="setup-label">Target Role (Required)</label>
                <CustomDropdown
                  options={INTERVIEW_ROLES.map(r => ({ value: r.title, label: r.title }))}
                  value={selectedRole}
                  onChange={(val) => setSelectedRole(val)}
                  searchable={true}
                />
              </div>

              {interviewMode === 'CODING_INTERVIEW' && (
                <div className="setup-field">
                  <label className="setup-label">Programming Language</label>
                  <CustomDropdown
                    options={languageOptions}
                    value={codingLanguage}
                    onChange={(val) => setCodingLanguage(val)}
                    searchable={true}
                  />
                </div>
              )}

              {interviewMode !== 'CODING_INTERVIEW' && (
                <>
                  <div className="setup-field" style={{ marginTop: '32px', paddingTop: '32px', borderTop: '1px solid var(--border)' }}>
                    <label className="setup-label">Enable Voice Interviewer</label>
                    <label className="setup-toggle">
                      <input type="checkbox" checked={voiceEnabled} onChange={(e) => setVoiceEnabled(e.target.checked)} />
                      <span>{voiceEnabled ? 'On' : 'Off'}</span>
                    </label>
                  </div>

                  {voiceEnabled && (
                    <>
                      <div className="setup-field">
                        <label className="setup-label">Interviewer Voice</label>
                        {voicesLoading ? (
                          <p className="setup-voice-hint">Loading voices…</p>
                        ) : (
                          <CustomDropdown
                            options={[
                              { value: '', label: 'Default Voice' },
                              ...murfVoices.map(v => ({ value: v.voiceId, label: `${v.name} (${v.gender || '—'}, ${v.language || v.locale || 'en'})` }))
                            ]}
                            value={selectedVoiceId}
                            onChange={(val) => {
                              setSelectedVoiceId(val);
                              const v = murfVoices.find(x => x.voiceId === val);
                              setSelectedStyle(v && v.styles && v.styles.length ? v.styles[0] : '');
                            }}
                            searchable={true}
                          />
                        )}
                      </div>

                      {(() => {
                        const current = murfVoices.find((x) => x.voiceId === selectedVoiceId);
                        if (!current || !current.styles || current.styles.length === 0) return null;
                        return (
                          <div className="setup-field">
                            <label className="setup-label">Speaking Style</label>
                            <CustomDropdown
                              options={current.styles.map(s => ({ value: s, label: s }))}
                              value={selectedStyle}
                              onChange={(val) => setSelectedStyle(val)}
                            />
                          </div>
                        );
                      })()}

                      <div className="setup-field" style={{ marginTop: '20px' }}>
                        <label className="setup-label">Speaking Speed: {voiceSpeed.toFixed(2)}x</label>
                        <div className="setup-speed-control-row">
                          <input
                            type="range"
                            min="0.5"
                            max="2"
                            step="0.1"
                            value={voiceSpeed}
                            onChange={(e) => setVoiceSpeed(parseFloat(e.target.value))}
                            className="setup-range"
                          />
                          <button 
                            className="setup-voice-preview-btn"
                            onClick={() => {
                               if (!window.speechSynthesis) return;
                               window.speechSynthesis.cancel();
                               const u = new SpeechSynthesisUtterance("Hi there! I will be your interviewer today.");
                               u.rate = voiceSpeed;
                               window.speechSynthesis.speak(u);
                            }}
                          >
                            <FaVolumeUp /> Preview
                          </button>
                        </div>
                      </div>
                    </>
                  )}
                </>
              )}
            </div>
          )}

          {currentStep.id === 'start' && (
            <div className="setup-section" style={{ textAlign: 'center' }}>
              <h2 className="setup-section-heading">Ready to begin!</h2>
              <p style={{ color: '#ccc', marginBottom: '2rem' }}>
                You have selected the <strong>{selectedRole}</strong> role with a <strong>{selectedDifficulty}</strong> difficulty level.
              </p>
              <BsCheckCircleFill style={{ fontSize: '4rem', color: '#00cc66', marginBottom: '2rem' }} />
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
