import getScoreColor from '../../constants/scoreColors.js';
import './index.css';

function ScoreCard({ label, score, comment, placeholder = false }) {
  const color = placeholder ? '#94a3b8' : getScoreColor(score);

  return (
    <div className="score-card" style={placeholder ? { opacity: 0.7 } : undefined}>
      <div className="score-card-header">
        <span className="score-card-label">{label}</span>
        <span className="score-card-value" style={{ color }}>
          {placeholder ? 'N/A' : `${score}/100`}
        </span>
      </div>
      <div className="score-bar-track">
        <div
          className="score-bar-fill"
          style={{ width: placeholder ? '0%' : `${score}%`, backgroundColor: color }}
        />
      </div>
      {comment && <p className="score-card-comment">{comment}</p>}
    </div>
  );
}

export default ScoreCard;
