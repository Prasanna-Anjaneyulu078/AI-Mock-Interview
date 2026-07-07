// ============================================
// difficulty.js - Interview Difficulty Levels
// ============================================
// Stars field stores count of filled stars (1-3).
// Rendered using BsStarFill/BsStar from react-icons.
// Reference: React Icons - reference-react.md
// ============================================

const DIFFICULTY_LEVELS = [
  {
    id: 'easy',
    label: 'Starter',
    stars: 1,
    questions: 4,
    description: '4 questions — warm-up + basics',
  },
  {
    id: 'medium',
    label: 'Standard',
    stars: 2,
    questions: 5,
    description: '5 questions — balanced mix',
  },
  {
    id: 'hard',
    label: 'Advanced',
    stars: 3,
    questions: 7,
    description: '7 questions — deep dive + coding',
  },
];

export default DIFFICULTY_LEVELS;
