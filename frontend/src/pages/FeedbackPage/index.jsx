import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getInterview } from '../../services/interviewService.js';
import ScoreCard from '../../components/ScoreCard';
import getScoreColor from '../../constants/scoreColors.js';
import {
  BsCheckCircleFill,
  BsArrowUpRight,
  BsJournalText,
  BsArrowRepeat,
} from 'react-icons/bs';
import toast from 'react-hot-toast';
import './index.css';

function FeedbackPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [interview, setInterview] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let retryCount = 0;
    const MAX_RETRIES = 8; // wait up to ~16 seconds for feedback to be generated
    const RETRY_DELAY_MS = 2000;

    const loadFeedback = async () => {
      try {
        const data = await getInterview(id);

        // FIX: Race condition — feedback may not be saved yet if we navigated immediately
        // after the last answer. Retry a few times before giving up.
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

  // FIX: overallScore can come from two places depending on which API responded
  // 1. interview.overallScore (set by InterviewMapper from interview.score)
  // 2. interview.feedback.overallScore (parsed from the Gemini JSON feedback)
  const overallScore = interview.overallScore
    ?? (typeof feedback === 'object' && feedback !== null ? feedback.overallScore : null)
    ?? 0;

  const { categoryScores, strengths, areasOfImprovement, finalAssessment } =
    typeof feedback === 'object' && feedback !== null ? feedback : {};

  return (
    <div className="feedback-page">
      <div className="feedback-container">
        <div className="feedback-header">
          <h1 className="feedback-heading">Interview Feedback</h1>
          <p className="feedback-role-text">{role}</p>
          <p className="feedback-date-text">
            {new Date(interview.createdAt).toLocaleDateString('en-US', {
              weekday: 'long',
              month: 'long',
              day: 'numeric',
              year: 'numeric',
            })}
          </p>
        </div>

        <div className="feedback-overall-section">
          <div
            className="feedback-score-circle"
            style={{ borderColor: getScoreColor(overallScore) }}
          >
            <span
              className="feedback-score-number"
              style={{ color: getScoreColor(overallScore) }}
            >
              {overallScore}
            </span>
            <span className="feedback-score-label">/100</span>
          </div>
          <h2 className="feedback-overall-title">Overall Score</h2>
        </div>

        <div className="feedback-categories-section">
          <h2 className="feedback-section-heading">Category Breakdown</h2>
          <div className="feedback-scores-grid">
            {categoryScores && (
              <>
                <ScoreCard
                  label="Communication Skills"
                  score={categoryScores.communicationSkills?.score || 0}
                  comment={categoryScores.communicationSkills?.comment}
                />
                <ScoreCard
                  label="Technical Knowledge"
                  score={categoryScores.technicalKnowledge?.score || 0}
                  comment={categoryScores.technicalKnowledge?.comment}
                />
                <ScoreCard
                  label="Problem Solving"
                  score={categoryScores.problemSolving?.score || 0}
                  comment={categoryScores.problemSolving?.comment}
                />
                <ScoreCard
                  label="Code Quality"
                  score={categoryScores.codeQuality?.score || 0}
                  comment={categoryScores.codeQuality?.comment}
                />
                <ScoreCard
                  label="Confidence"
                  score={categoryScores.confidence?.score || 0}
                  comment={categoryScores.confidence?.comment}
                />
              </>
            )}
          </div>
        </div>

        {strengths && strengths.length > 0 && (
          <div className="feedback-callout feedback-callout-success">
            <div className="feedback-callout-header">
              <BsCheckCircleFill className="feedback-callout-icon-success" />
              <h2 className="feedback-callout-heading">Strengths</h2>
            </div>
            <ul className="feedback-callout-list">
              {strengths.map((item, index) => (
                <li key={index} className="feedback-callout-list-item">
                  {item}
                </li>
              ))}
            </ul>
          </div>
        )}

        {areasOfImprovement && areasOfImprovement.length > 0 && (
          <div className="feedback-callout feedback-callout-warning">
            <div className="feedback-callout-header">
              <BsArrowUpRight className="feedback-callout-icon-warning" />
              <h2 className="feedback-callout-heading">
                Areas for Improvement
              </h2>
            </div>
            <ul className="feedback-callout-list">
              {areasOfImprovement.map((item, index) => (
                <li key={index} className="feedback-callout-list-item">
                  {item}
                </li>
              ))}
            </ul>
          </div>
        )}

        {finalAssessment && (
          <div className="feedback-callout feedback-callout-blue">
            <div className="feedback-callout-header">
              <BsJournalText className="feedback-callout-icon-blue" />
              <h2 className="feedback-callout-heading">Final Assessment</h2>
            </div>
            <p className="feedback-assessment-text">{finalAssessment}</p>
          </div>
        )}

        <div className="feedback-actions-row">
          <button
            className="feedback-btn-primary"
            onClick={() => navigate('/setup')}
          >
            <BsArrowRepeat className="feedback-btn-icon" />
            Retake Interview
          </button>
          <button
            className="feedback-btn-outline"
            onClick={() => navigate('/')}
          >
            Back to Dashboard
          </button>
        </div>
      </div>
    </div>
  );
}

export default FeedbackPage;
