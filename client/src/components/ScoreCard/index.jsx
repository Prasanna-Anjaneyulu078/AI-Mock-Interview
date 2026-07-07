import getScoreColor from '../../constants/scoreColors.js';
import './index.css';

function ScoreCard({ label, score, comment }) {
  const color = getScoreColor(score);

  return (
    <div className="score-card">
      <div className="score-card-header">
        <span className="score-card-label">{label}</span>
        <span className="score-card-value" style={{ color }}>
          {score}/100
        </span>
      </div>
      <div className="score-bar-track">
        <div
          className="score-bar-fill"
          style={{ width: `${score}%`, backgroundColor: color }}
        />
      </div>
      {comment && <p className="score-card-comment">{comment}</p>}
    </div>
  );
}

export default ScoreCard;
