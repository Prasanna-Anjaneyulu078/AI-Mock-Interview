// ============================================
// difficulty.js - Interview Difficulty Levels
// ============================================
// Updated question counts to match target:
//   HR/Behavioral:  10 questions
//   Technical:      12 questions
//   Mixed/Advanced: 15 questions
// ============================================

const DIFFICULTY_LEVELS = [
  {
    id: 'easy',
    label: 'Starter',
    stars: 1,
    questions: 10,
    description: '10 questions — warm-up + basics',
  },
  {
    id: 'medium',
    label: 'Standard',
    stars: 2,
    questions: 12,
    description: '12 questions — balanced mix',
  },
  {
    id: 'hard',
    label: 'Advanced',
    stars: 3,
    questions: 15,
    description: '15 questions — deep dive + coding',
  },
];

export default DIFFICULTY_LEVELS;
