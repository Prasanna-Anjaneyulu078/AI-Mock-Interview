import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getInterview } from '../../services/interviewService.js';
import ScoreCard from '../../components/ScoreCard';
import getScoreColor from '../../constants/scoreColors.js';
import {
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
  Tooltip,
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
    { subject: 'Communication', score: categoryScores.communicationSkills?.score ?? 0 },
    { subject: 'Technical', score: categoryScores.technicalKnowledge?.score ?? 0 },
    { subject: 'Project', score: categoryScores.projectScore?.score ?? 0 },
    { subject: 'Code Quality', score: categoryScores.codeQuality?.score ?? 0 },
    { subject: 'Confidence', score: categoryScores.confidence?.score ?? 0 },
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

function FeedbackPage() {
  const { id } = useParams();
  const navigate = useNavigate();

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
    interviewSummary,
    finalAssessment,
    hiringRecommendation,
    category,
    recommendedTopics,
    weakConcepts,
    strongConcepts,
  } = typeof feedback === 'object' && feedback !== null ? feedback : {};

  const evaluated = feedback?.evaluated !== false;

  const radarData = buildRadarData(categoryScores);

  const hiringKey = hiringRecommendation || 'Borderline';
  const hiringCfg = HIRING_CONFIG[hiringKey] || HIRING_CONFIG['Borderline'];
  const HiringIcon = hiringCfg.icon;

  const summaryText = interviewSummary || finalAssessment;

  return (
    <div className="feedback-page">
      <div className="feedback-container">

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
          <h1 className="feedback-heading">Interview Feedback</h1>
          <p className="feedback-role-text">{role}</p>
          <p className="feedback-date-text">
            {new Date(interview.createdAt).toLocaleDateString('en-US', {
              weekday: 'long', month: 'long', day: 'numeric', year: 'numeric',
            })}
          </p>
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
                  score={categoryScores.communicationSkills?.score ?? 0}
                  comment={categoryScores.communicationSkills?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Technical Knowledge"
                  score={categoryScores.technicalKnowledge?.score ?? 0}
                  comment={categoryScores.technicalKnowledge?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Project Score"
                  score={categoryScores.projectScore?.score ?? 0}
                  comment={categoryScores.projectScore?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Code Quality"
                  score={categoryScores.codeQuality?.score ?? 0}
                  comment={categoryScores.codeQuality?.comment}
                  placeholder={!evaluated}
                />
                <ScoreCard
                  label="Confidence"
                  score={categoryScores.confidence?.score ?? 0}
                  comment={categoryScores.confidence?.comment}
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
        {recommendedTopics && recommendedTopics.length > 0 && (
          <div className="feedback-callout feedback-callout-blue">
            <div className="feedback-callout-header">
              <BsLightbulbFill className="feedback-callout-icon-blue" />
              <h2 className="feedback-callout-heading">Recommended Topics to Study</h2>
            </div>
            <ul className="feedback-callout-list">
              {recommendedTopics.map((item, idx) => (
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

        {/* ── Actions ── */}
        <div className="feedback-actions-row">
          <button className="feedback-btn-primary" onClick={() => navigate('/setup')}>
            <BsArrowRepeat className="feedback-btn-icon" />
            Retake Interview
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
