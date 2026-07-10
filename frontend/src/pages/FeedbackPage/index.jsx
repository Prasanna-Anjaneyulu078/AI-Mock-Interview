import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import html2canvas from 'html2canvas';
import jsPDF from 'jspdf';
import { getInterview } from '../../services/interviewService.js';
import ScoreCard from '../../components/ScoreCard';
import getScoreColor from '../../constants/scoreColors.js';
import AudioPlayer from '../../components/AudioPlayer';
import {
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
  Tooltip,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
} from 'recharts';
import {
  BsCheckCircleFill,
  BsArrowUpRight,
  BsJournalText,
  BsArrowRepeat,
  BsLightbulbFill,
  BsPersonBadgeFill,
  BsXCircleFill,
  BsQuestionCircleFill,
} from 'react-icons/bs';
import toast from 'react-hot-toast';
import './index.css';

// ─── Hiring badge config ──────────────────────────────────────────────────────
const HIRING_CONFIG = {
  Hire: {
    icon: BsCheckCircleFill,
    label: 'Hire',
    bg: 'linear-gradient(135deg,#d1fae5,#a7f3d0)',
    border: '#6ee7b7',
    color: '#065f46',
  },
  'No Hire': {
    icon: BsXCircleFill,
    label: 'No Hire',
    bg: 'linear-gradient(135deg,#fee2e2,#fecaca)',
    border: '#f87171',
    color: '#7f1d1d',
  },
  Borderline: {
    icon: BsQuestionCircleFill,
    label: 'Borderline',
    bg: 'linear-gradient(135deg,#fef3c7,#fde68a)',
    border: '#fbbf24',
    color: '#78350f',
  },
};

// ─── Radar data builder ───────────────────────────────────────────────────────
function buildRadarData(categoryScores) {
  if (!categoryScores) return [];
  return [
    { subject: 'Communication', score: categoryScores.communicationScore?.score ?? 0 },
    { subject: 'Technical', score: categoryScores.technicalScore?.score ?? 0 },
    { subject: 'Problem Solving', score: categoryScores.problemSolvingScore?.score ?? 0 },
    { subject: 'Coding', score: categoryScores.codingScore?.score ?? 0 },
    { subject: 'Confidence', score: categoryScores.confidenceScore?.score ?? 0 },
  ];
}

// ─── Custom Radar tooltip ─────────────────────────────────────────────────────
function CustomTooltip({ active, payload }) {
  if (active && payload && payload.length) {
    const { subject, score } = payload[0].payload;
    return (
      <div style={{
        background: '#1e293b', color: '#f8fafc', border: '1px solid #334155',
        borderRadius: 10, padding: '8px 14px', fontSize: '0.88rem',
      }}>
        <strong>{subject}</strong>: {score}/100
      </div>
    );
  }
  return null;
}

// ─── Difficulty Data Builder ──────────────────────────────────────────────────
function buildDifficultyData(questions) {
  if (!questions) return [];
  const levelMap = { 'BEGINNER': 1, 'INTERMEDIATE': 2, 'ADVANCED': 3 };
  return questions.map((q, idx) => {
    const ans = q.answers && q.answers.length > 0 ? q.answers[0] : {};
    const level = ans.difficultyLevel || q.difficulty || 'INTERMEDIATE';
    return {
      name: `Q${idx + 1}`,
      level: levelMap[level.toUpperCase()] || 2,
      label: level,
    };
  });
}

function FeedbackPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const reportRef = useRef(null);

  const [interview, setInterview] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let retryCount = 0;
    const MAX_RETRIES = 8;
    const RETRY_DELAY_MS = 2000;

    const loadFeedback = async () => {
      try {
        const data = await getInterview(id);

        if (!data.feedback) {
          if (retryCount < MAX_RETRIES) {
            retryCount++;
            setTimeout(loadFeedback, RETRY_DELAY_MS);
            return;
          }
          toast.error('No feedback available for this interview.');
          navigate('/');
          return;
        }

        setInterview(data);
      } catch (error) {
        toast.error('Failed to load feedback');
        navigate('/');
      } finally {
        setLoading(false);
      }
    };

    loadFeedback();
  }, [id, navigate]);

  if (loading) {
    return (
      <div className="feedback-loading-state">
        <div className="spinner-border spinner-border-sm" role="status" />
        <p className="feedback-loading-text">Generating your feedback report...</p>
        <p style={{ color: 'var(--text-muted, #888)', fontSize: '0.85rem', marginTop: '0.5rem' }}>
          AI is analysing your answers. This may take a few seconds...
        </p>
      </div>
    );
  }

  if (!interview || !interview.feedback) return null;

  const { feedback, role } = interview;

  const overallScore = interview.overallScore
    ?? (typeof feedback === 'object' && feedback !== null ? feedback.overallScore : null)
    ?? 0;

  const {
    categoryScores,
    strengths,
    areasOfImprovement,
    missedConcepts,
    recommendedLearningTopics,
    weakConcepts,
    strongConcepts,
    codingPerformance,
    careerGuidance,
    interviewSummary,
    finalAssessment,
    hiringRecommendation,
    category,
  } = typeof feedback === 'object' && feedback !== null ? feedback : {};

  const evaluated = feedback?.evaluated !== false;

  const radarData = buildRadarData(categoryScores);

  const hiringKey = hiringRecommendation || 'Borderline';
  const hiringCfg = HIRING_CONFIG[hiringKey] || HIRING_CONFIG['Borderline'];
  const HiringIcon = hiringCfg.icon;

  const summaryText = interviewSummary || finalAssessment;

  const downloadPDF = async () => {
    if (!reportRef.current) return;
    try {
      const toastId = toast.loading('Generating PDF report...');
      const canvas = await html2canvas(reportRef.current, { scale: 2 });
      const imgData = canvas.toDataURL('image/png');
      const pdf = new jsPDF('p', 'mm', 'a4');
      
      const pdfWidth = pdf.internal.pageSize.getWidth();
      const pdfHeight = (canvas.height * pdfWidth) / canvas.width;
      
      pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);
      pdf.save(`Interview_Report_${id}.pdf`);
      toast.success('PDF downloaded successfully!', { id: toastId });
    } catch (error) {
      toast.error('Failed to generate PDF');
    }
  };

  return (
    <div className="feedback-page">
      <div className="feedback-container" ref={reportRef}>

        {/* ── Scoring unavailable banner ── */}
        {!evaluated && (
          <div
            role="alert"
            style={{
              display: 'flex', gap: '0.75rem', alignItems: 'flex-start',
              background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b',
              borderRadius: 12, padding: '1rem 1.25rem', marginBottom: '1.5rem',
            }}
          >
            <BsJournalText style={{ color: '#dc2626', flexShrink: 0, marginTop: 2 }} />
            <div>
              <strong style={{ display: 'block', marginBottom: '0.25rem' }}>
                Scoring unavailable — these scores are placeholders
              </strong>
              <span style={{ fontSize: '0.9rem', lineHeight: 1.5 }}>
                The AI evaluation service was unreachable when this interview ended.
                The 60/100 scores are neutral placeholders and do <strong>not</strong> reflect
                your actual performance.
              </span>
            </div>
          </div>
        )}

        {/* ── Header ── */}
        <div className="feedback-header">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h1 className="feedback-heading">Interview Feedback</h1>
              <p className="feedback-role-text">{role}</p>
              <p className="feedback-date-text">
                {new Date(interview.createdAt).toLocaleDateString('en-US', {
                  weekday: 'long', month: 'long', day: 'numeric', year: 'numeric',
                })}
              </p>
            </div>
            
            {/* Hiring Recommendation Badge */}
            <div style={{
              display: 'flex', alignItems: 'center', gap: '0.75rem',
              background: hiringCfg.bg, border: `1px solid ${hiringCfg.border}`,
              color: hiringCfg.color, padding: '0.75rem 1.25rem', borderRadius: '12px',
              fontWeight: 700, fontSize: '1.25rem'
            }}>
              <HiringIcon size={24} />
              <span>{hiringCfg.label}</span>
            </div>
          </div>
        </div>

        {/* ── Overall Score + Hiring Verdict ── */}
        <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap', alignItems: 'stretch', marginBottom: '2rem' }}>
          {/* Score circle */}
          <div className="feedback-overall-section" style={{ flex: '0 0 auto' }}>
            <div
              className="feedback-score-circle"
              style={{ borderColor: getScoreColor(evaluated ? overallScore : null) }}
            >
              <span
                className="feedback-score-number"
                style={{ color: getScoreColor(evaluated ? overallScore : null) }}
              >
                {evaluated ? Math.round(overallScore) : 'N/A'}
              </span>
              <span className="feedback-score-label">/100</span>
            </div>
            <h2 className="feedback-overall-title">Overall Score</h2>
            {category && (
              <p style={{ marginTop: '0.4rem', fontWeight: 600, color: getScoreColor(overallScore), fontSize: '0.95rem' }}>
                {category}
              </p>
            )}
          </div>

          {/* Hiring verdict */}
          {evaluated && (
            <div style={{
              flex: '1 1 220px', background: hiringCfg.bg,
              border: `1.5px solid ${hiringCfg.border}`, borderRadius: 16,
              padding: '1.5rem', display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center', gap: '0.75rem',
            }}>
              <HiringIcon size={38} style={{ color: hiringCfg.color }} />
              <p style={{ margin: 0, fontWeight: 700, fontSize: '1.35rem', color: hiringCfg.color }}>
                {hiringCfg.label}
              </p>
              <p style={{ margin: 0, fontSize: '0.85rem', color: hiringCfg.color, opacity: 0.8, textAlign: 'center' }}>
                Hiring Recommendation
              </p>
            </div>
          )}
        </div>

        {/* ── Radar Chart ── */}
        {evaluated && radarData.length > 0 && (
          <div style={{ marginBottom: '2rem' }}>
            <h2 className="feedback-section-heading">Performance Radar</h2>
            <ResponsiveContainer width="100%" height={300}>
              <RadarChart data={radarData} margin={{ top: 10, right: 30, bottom: 10, left: 30 }}>
                <PolarGrid stroke="#334155" />
                <PolarAngleAxis
                  dataKey="subject"
                  tick={{ fill: '#94a3b8', fontSize: 13, fontWeight: 600 }}
                />
                <PolarRadiusAxis
                  angle={30}
                  domain={[0, 100]}
                  tick={{ fill: '#64748b', fontSize: 11 }}
                />
                <Radar
                  name="Score"
                  dataKey="score"
                  stroke="#818cf8"
                  fill="#818cf8"
                  fillOpacity={0.35}
                  strokeWidth={2}
                />
                <Tooltip content={<CustomTooltip />} />
              </RadarChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* ── Category Score Cards ── */}
        <div className="feedback-categories-section">
          <h2 className="feedback-section-heading">Category Breakdown</h2>
          <div className="feedback-scores-grid">
            {categoryScores && (
              <>
                <ScoreCard
                  label="Communication"
                  score={categoryScores.communicationScore?.score ?? 0}
                  comment={categoryScores.communicationScore?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Technical Knowledge"
                  score={categoryScores.technicalScore?.score ?? 0}
                  comment={categoryScores.technicalScore?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Problem Solving"
                  score={categoryScores.problemSolvingScore?.score ?? 0}
                  comment={categoryScores.problemSolvingScore?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Coding Score"
                  score={categoryScores.codingScore?.score ?? 0}
                  comment={categoryScores.codingScore?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Confidence"
                  score={categoryScores.confidenceScore?.score ?? 0}
                  comment={categoryScores.confidenceScore?.comment}
                  placeholder={!evaluated}
                />
              </>
            )}
          </div>
        </div>

        {/* ── Strengths ── */}
        {strengths && strengths.length > 0 && (
          <div className="feedback-callout feedback-callout-success">
            <div className="feedback-callout-header">
              <BsCheckCircleFill className="feedback-callout-icon-success" />
              <h2 className="feedback-callout-heading">Strengths</h2>
            </div>
            <ul className="feedback-callout-list">
              {strengths.map((item, idx) => (
                <li key={idx} className="feedback-callout-list-item">{item}</li>
              ))}
            </ul>
          </div>
        )}

        {/* ── Areas for Improvement ── */}
        {areasOfImprovement && areasOfImprovement.length > 0 && (
          <div className="feedback-callout feedback-callout-warning">
            <div className="feedback-callout-header">
              <BsArrowUpRight className="feedback-callout-icon-warning" />
              <h2 className="feedback-callout-heading">Areas for Improvement</h2>
            </div>
            <ul className="feedback-callout-list">
              {areasOfImprovement.map((item, idx) => (
                <li key={idx} className="feedback-callout-list-item">{item}</li>
              ))}
            </ul>
          </div>
        )}

        {/* ── Strong Concepts ── */}
        {strongConcepts && strongConcepts.length > 0 && (
          <div className="feedback-callout feedback-callout-success">
            <div className="feedback-callout-header">
              <BsPersonBadgeFill className="feedback-callout-icon-success" />
              <h2 className="feedback-callout-heading">Strong Concepts</h2>
            </div>
            <ul className="feedback-callout-list">
              {strongConcepts.map((item, idx) => (
                <li key={idx} className="feedback-callout-list-item">{item}</li>
              ))}
            </ul>
          </div>
        )}

        {/* ── Missed Concepts ── */}
        {missedConcepts && missedConcepts.length > 0 && (
          <div className="feedback-callout feedback-callout-warning">
            <div className="feedback-callout-header">
              <BsXCircleFill className="feedback-callout-icon-warning" />
              <h2 className="feedback-callout-heading">Missed Concepts</h2>
            </div>
            <ul className="feedback-callout-list">
              {missedConcepts.map((item, idx) => (
                <li key={idx} className="feedback-callout-list-item">{item}</li>
              ))}
            </ul>
          </div>
        )}

        {/* ── Weak Concepts ── */}
        {weakConcepts && weakConcepts.length > 0 && (
          <div className="feedback-callout feedback-callout-warning">
            <div className="feedback-callout-header">
              <BsXCircleFill className="feedback-callout-icon-warning" />
              <h2 className="feedback-callout-heading">Weak Concepts to Review</h2>
            </div>
            <ul className="feedback-callout-list">
              {weakConcepts.map((item, idx) => (
                <li key={idx} className="feedback-callout-list-item">{item}</li>
              ))}
            </ul>
          </div>
        )}

        {/* ── Recommended Topics ── */}
        {recommendedLearningTopics && recommendedLearningTopics.length > 0 && (
          <div className="feedback-callout feedback-callout-blue">
            <div className="feedback-callout-header">
              <BsLightbulbFill className="feedback-callout-icon-blue" />
              <h2 className="feedback-callout-heading">Recommended Topics to Study</h2>
            </div>
            <ul className="feedback-callout-list">
              {recommendedLearningTopics.map((item, idx) => (
                <li key={idx} className="feedback-callout-list-item">{item}</li>
              ))}
            </ul>
          </div>
        )}

        {/* ── Interview Summary ── */}
        {summaryText && (
          <div className="feedback-callout feedback-callout-blue">
            <div className="feedback-callout-header">
              <BsJournalText className="feedback-callout-icon-blue" />
              <h2 className="feedback-callout-heading">Interview Summary</h2>
            </div>
            <p className="feedback-assessment-text">{summaryText}</p>
          </div>
        )}

        {/* ── Coding Performance ── */}
        {codingPerformance && (
          <div className="feedback-callout feedback-callout-blue">
            <div className="feedback-callout-header">
              <BsJournalText className="feedback-callout-icon-blue" />
              <h2 className="feedback-callout-heading">Coding Performance</h2>
            </div>
            <p className="feedback-assessment-text">{codingPerformance}</p>
          </div>
        )}

        {/* ── Career Guidance ── */}
        {careerGuidance && (
          <div className="feedback-callout feedback-callout-blue">
            <div className="feedback-callout-header">
              <BsJournalText className="feedback-callout-icon-blue" />
              <h2 className="feedback-callout-heading">Career Guidance</h2>
            </div>
            <p className="feedback-assessment-text">{careerGuidance}</p>
          </div>
        )}

        {/* ── Adaptive Difficulty Progression ── */}
        {interview.questions && interview.questions.length > 0 && (
          <div className="feedback-callout" style={{ background: '#fff', border: '1px solid #e2e8f0' }}>
            <div className="feedback-callout-header" style={{ marginBottom: '1.5rem' }}>
              <BsGraphUp style={{ color: '#3b82f6', fontSize: '1.25rem', marginRight: '0.5rem' }} />
              <h2 className="feedback-callout-heading" style={{ color: '#0f172a' }}>Difficulty Progression</h2>
            </div>
            <p style={{ color: '#475569', fontSize: '0.9rem', marginBottom: '1.5rem' }}>
              This chart tracks how the adaptive engine adjusted the difficulty of your questions based on your real-time performance.
            </p>
            <div style={{ height: 250, width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={buildDifficultyData(interview.questions)} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                  <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: '#64748b' }} dy={10} />
                  <YAxis 
                    domain={[1, 3]} 
                    ticks={[1, 2, 3]} 
                    tickFormatter={(val) => val === 1 ? 'Beginner' : val === 2 ? 'Intermediate' : 'Advanced'}
                    axisLine={false} 
                    tickLine={false} 
                    tick={{ fontSize: 12, fill: '#64748b' }} 
                    width={90}
                  />
                  <Tooltip 
                    contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)' }}
                    formatter={(value, name, props) => [props.payload.label, 'Level']}
                  />
                  <Line type="monotone" dataKey="level" stroke="#3b82f6" strokeWidth={3} dot={{ r: 4, fill: '#3b82f6', strokeWidth: 2, stroke: '#fff' }} activeDot={{ r: 6 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}

        {/* ── Question Breakdown ── */}
        {interview.questions && interview.questions.length > 0 && (
          <div className="feedback-questions-section" style={{ marginTop: '3rem', marginBottom: '3rem' }}>
            <h2 className="feedback-section-heading">Question Breakdown</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
              {interview.questions.map((q, idx) => (
                <div key={idx} style={{ 
                  background: '#fff', border: '1px solid #e2e8f0', 
                  borderRadius: '12px', padding: '1.5rem', boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
                }}>
                  <h3 style={{ fontSize: '1.1rem', color: '#0f172a', marginBottom: '0.5rem', fontWeight: 600 }}>
                    Q{idx + 1}: {q.questionText}
                  </h3>
                  
                  {q.codeLanguage && q.codeSnippet && (
                    <div style={{ margin: '1rem 0', background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                      <strong style={{ display: 'block', marginBottom: '0.5rem', color: '#475569', fontSize: '0.9rem' }}>
                        Submitted Code ({q.codeLanguage})
                      </strong>
                      <pre style={{ margin: 0, overflowX: 'auto', fontSize: '0.9rem', fontFamily: 'monospace', color: '#1e293b' }}>
                        {q.codeSnippet}
                      </pre>
                      {q.executionStatus && (
                        <p style={{ marginTop: '0.5rem', fontSize: '0.85rem', color: q.executionStatus.toLowerCase().includes('pass') ? '#059669' : '#dc2626', fontWeight: 600 }}>
                          Status: {q.executionStatus}
                        </p>
                      )}
                    </div>
                  )}

                  {!q.codeSnippet && q.recordingUrl && (
                    <div style={{ marginBottom: '1rem', padding: '1rem', background: '#f8fafc', borderRadius: '8px' }}>
                      <strong style={{ display: 'block', marginBottom: '0.5rem', color: '#475569', fontSize: '0.9rem' }}>
                        Your Voice Recording:
                      </strong>
                      <AudioPlayer audioSrc={q.recordingUrl.startsWith('http') ? q.recordingUrl : `http://localhost:8082${q.recordingUrl}`} autoPlay={false} />
                    </div>
                  )}

                  {!q.codeSnippet && (
                    <div style={{ marginBottom: '1rem' }}>
                      <strong style={{ color: '#475569', fontSize: '0.9rem' }}>Your Answer:</strong>
                      <p style={{ color: '#334155', fontSize: '0.95rem', marginTop: '0.25rem' }}>
                        {q.candidateAnswer || 'No answer provided'}
                      </p>
                    </div>
                  )}

                  {/* Communication Analytics */}
                  {(q.speakingSpeed !== null && q.speakingSpeed !== undefined) && (
                    <div style={{ 
                      display: 'flex', gap: '1.5rem', marginBottom: '1rem', 
                      background: '#eff6ff', padding: '0.75rem 1rem', 
                      borderRadius: '8px', border: '1px solid #bfdbfe'
                    }}>
                      <div>
                        <span style={{ color: '#1e40af', fontSize: '0.85rem', fontWeight: 600, display: 'block' }}>Speaking Speed</span>
                        <span style={{ color: '#1e3a8a', fontSize: '1.1rem', fontWeight: 700 }}>{q.speakingSpeed} wpm</span>
                      </div>
                      <div>
                        <span style={{ color: '#1e40af', fontSize: '0.85rem', fontWeight: 600, display: 'block' }}>Filler Words</span>
                        <span style={{ color: '#1e3a8a', fontSize: '1.1rem', fontWeight: 700 }}>{q.fillerWordsCount}</span>
                      </div>
                      <div>
                        <span style={{ color: '#1e40af', fontSize: '0.85rem', fontWeight: 600, display: 'block' }}>Fluency Score</span>
                        <span style={{ color: '#1e3a8a', fontSize: '1.1rem', fontWeight: 700 }}>{q.fluencyScore}/100</span>
                      </div>
                    </div>
                  )}

                  <div style={{ background: '#f1f5f9', padding: '1rem', borderRadius: '8px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                      <strong style={{ color: '#0f172a' }}>AI Feedback</strong>
                      <span style={{ 
                        background: '#e0e7ff', color: '#3730a3', padding: '2px 8px', 
                        borderRadius: '999px', fontSize: '0.85rem', fontWeight: 600 
                      }}>
                        Score: {q.score ? Math.round(q.score) : 'N/A'}/100
                      </span>
                    </div>
                    <p style={{ color: '#334155', fontSize: '0.95rem', margin: 0 }}>
                      {q.feedback || 'No feedback available'}
                    </p>
                    {q.improvementSuggestions && (
                      <p style={{ color: '#64748b', fontSize: '0.9rem', marginTop: '0.5rem', fontStyle: 'italic' }}>
                        Tip: {q.improvementSuggestions}
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Actions ── */}
        <div className="feedback-actions-row">
          <button className="feedback-btn-primary" onClick={() => navigate('/setup')}>
            <BsArrowRepeat className="feedback-btn-icon" />
            Retake Interview
          </button>
          <button className="feedback-btn-primary" style={{ background: '#475569' }} onClick={downloadPDF}>
            <BsJournalText className="feedback-btn-icon" />
            Download PDF Report
          </button>
          <button className="feedback-btn-outline" onClick={() => navigate('/')}>
            Back to Dashboard
          </button>
        </div>

      </div>
    </div>
  );
}

export default FeedbackPage;
