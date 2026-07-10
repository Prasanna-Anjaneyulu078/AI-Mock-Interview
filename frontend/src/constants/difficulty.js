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
    id: 'starter',
    label: 'Starter',
    stars: 1,
    questions: 7,
    // 1 introduction (HR) + 1 HR + 3 Technical + 2 Project = 7 total.
    // Difficulty drives question DEPTH (basic concepts), not just the count.
    description: '7 questions — Beginner (1 intro + 1 HR, 3 Tech, 2 Project). Basic concepts.',
  },
  {
    id: 'standard',
    label: 'Standard',
    stars: 2,
    questions: 15,
    // 1 introduction (HR) + 3 HR + 6 Technical + 5 Project = 15 total.
    description: '15 questions — Placement Prep (1 intro + 3 HR, 6 Tech, 5 Project). Practical & debugging.',
  },
  {
    id: 'advanced',
    label: 'Advanced',
    stars: 3,
    questions: 25,
    // 1 introduction (HR) + 4 HR + 10 Technical + 10 Project = 25 total.
    description: '25 questions — Product Companies (1 intro + 4 HR, 10 Tech, 10 Project). Architecture & scaling.',
  },
];

export default DIFFICULTY_LEVELS;
