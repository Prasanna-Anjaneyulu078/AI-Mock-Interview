import { MdDelete } from 'react-icons/md';
import getScoreColor from '../../constants/scoreColors.js';
import './index.css';

function InterviewCard({ interview, onClick, onDelete }) {
  const date = new Date(interview.createdAt).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  const statusClass =
    interview.status === 'completed' ? 'badge-success' : 'badge-warning';
  const statusLabel =
    interview.status === 'completed' ? 'Completed' : 'In Progress';

  return (
    <div className="interview-card" onClick={onClick}>
      <div className="interview-card-top">
        <h3 className="interview-card-role">{interview.role}</h3>
        <span className={`interview-badge ${statusClass}`}>{statusLabel}</span>
      </div>
      <div className="interview-card-meta">
        <span className="interview-card-date">{date}</span>
        <span className="interview-card-questions">
          {interview.totalQuestions} questions
        </span>
      </div>
      {interview.overallScore !== null &&
        interview.overallScore !== undefined && (
          <div className="interview-card-score">
            <span
              className="score-value"
              style={{ color: getScoreColor(interview.overallScore) }}
            >
              {interview.overallScore}
            </span>
            <span className="score-label">/100</span>
          </div>
        )}
      <div className="interview-card-footer">
        <button
          className="interview-card-delete"
          onClick={(e) => {
            e.stopPropagation();
            onDelete(interview._id);
          }}
        >
          <MdDelete className="delete-icon" />
          Delete
        </button>
      </div>
    </div>
  );
}

export default InterviewCard;
