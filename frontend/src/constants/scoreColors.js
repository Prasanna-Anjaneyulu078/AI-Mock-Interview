// ============================================
// scoreColors.js - Score Color Utility
// ============================================

// Returns a color from the Ocean Blue palette based on score value
// Reference: color-palettes.md (Ocean Blue status colors)
const getScoreColor = (score) => {
  if (!score && score !== 0) return '#64748b';
  if (score >= 80) return '#16a34a';
  if (score >= 60) return '#d97706';
  if (score >= 40) return '#d97706';
  return '#dc2626';
};

export default getScoreColor;
