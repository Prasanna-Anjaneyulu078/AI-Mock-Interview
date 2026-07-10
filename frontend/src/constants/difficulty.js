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
    id: 'BEGINNER',
    label: 'Beginner (Fresher)',
    stars: 1,
    description: 'Adaptive Engine: Starts Easy → Adjusts to Medium.',
  },
  {
    id: 'INTERMEDIATE',
    label: 'Intermediate (Experienced)',
    stars: 2,
    description: 'Adaptive Engine: Starts Medium → Adjusts to Hard.',
  },
  {
    id: 'ADVANCED',
    label: 'Advanced (Expert)',
    stars: 3,
    description: 'Adaptive Engine: Consistently Hard. Deep architectural discussions.',
  },
];

export default DIFFICULTY_LEVELS;
