// ============================================
// interviewModes.js — Interview mode + role definitions
// ============================================
// Single source of truth for the redesigned Interview Setup flow.
//
// Modes sent to the backend use the canonical vocabulary the API expects:
//   CODING | TECHNICAL | BEHAVIORAL | RESUME_BASED | SYSTEM_DESIGN
// (The backend also still accepts legacy aliases for compatibility.)
//
// Role ids are sent verbatim as `role` in the start-interview payload.
// ============================================

import {
  BsCodeSlash,
  BsCpu,
  BsPeople,
  BsFileText,
  BsDiagram3,
  BsServer,
  BsLightningFill,
  BsDatabase,
  BsWindowStack,
  BsHddNetwork,
  BsBoxes,
  BsChatDots,
  BsPersonBadge,
  BsClock,
  BsLightbulb,
  BsFileEarmarkText,
  BsBoxSeam,
  BsCloud,
  BsGraphUp,
  BsArrowUpRightCircle,
  BsRobot,
} from 'react-icons/bs';

import {
  FaJava,
  FaPython,
  FaReact,
  FaDatabase,
} from 'react-icons/fa';

// ─────────────────────────────────────────────
// Interview modes (card selection)
// ─────────────────────────────────────────────
export const MODES = [
  {
    id: 'CODING',
    title: 'Coding Interview',
    icon: BsCodeSlash,
    description: 'Solve algorithm & data-structure problems in a live IDE.',
    accent: '#f97316',
    questionCount: '10–20 Questions',
    difficulty: 'All Levels',
    interviewType: 'Live IDE',
  },
  {
    id: 'TECHNICAL',
    title: 'Technical Interview',
    icon: BsCpu,
    description: 'Core CS concepts, frameworks, and technical deep dives.',
    accent: '#3b82f6',
    questionCount: '10–20 Questions',
    difficulty: 'All Levels',
    interviewType: 'Q&A',
  },
  {
    id: 'BEHAVIORAL',
    title: 'Behavioral Interview',
    icon: BsPeople,
    description: 'Soft skills, leadership, and real-world experiences.',
    accent: '#a855f7',
    questionCount: '10–20 Questions',
    difficulty: 'All Levels',
    interviewType: 'Q&A',
  },
  {
    id: 'RESUME_BASED',
    title: 'Resume-Based Interview',
    icon: BsFileText,
    description: 'Questions generated from your uploaded resume.',
    accent: '#10b981',
    questionCount: '10–20 Questions',
    difficulty: 'All Levels',
    interviewType: 'Resume',
  }
];

// ─────────────────────────────────────────────
// Mode-specific role cards
// ─────────────────────────────────────────────
import { INTERVIEW_ROLES } from './roles';

export const ROLES_BY_MODE = {
  CODING: INTERVIEW_ROLES,
  TECHNICAL: INTERVIEW_ROLES,
  BEHAVIORAL: INTERVIEW_ROLES,
  RESUME_BASED: INTERVIEW_ROLES,
  SYSTEM_DESIGN: INTERVIEW_ROLES,
  MIXED: INTERVIEW_ROLES
};

// ─────────────────────────────────────────────
// Resume-upload behaviour per mode
// ─────────────────────────────────────────────
// required  : resume must be present before the interview can start
// helper    : helper text shown in the upload area (optional modes)
export const RESUME_POLICY = {
  CODING: { required: false, hidden: true, helper: null },
  TECHNICAL: { required: false, hidden: false, helper: 'Upload a resume to personalize technical questions.' },
  BEHAVIORAL: { required: false, hidden: false, helper: 'Upload a resume to personalize behavioral questions.' },
  RESUME_BASED: { required: true, hidden: false, helper: 'Resume is required for Resume-Based Interviews.' },
};

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────
export const getMode = (id) => MODES.find((m) => m.id === id) || null;
export const getRolesForMode = (modeId) => ROLES_BY_MODE[modeId] || [];
export const getResumePolicy = (modeId) => RESUME_POLICY[modeId] || RESUME_POLICY.TECHNICAL;
