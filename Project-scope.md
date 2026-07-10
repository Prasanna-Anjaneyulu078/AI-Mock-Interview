# AI Mock Interview Platform

## Project Overview

AI Mock Interview Platform is an AI-powered interview preparation system that generates personalized interview questions based on a candidate's resume, skills, projects, and target role.

The platform provides realistic mock interview experiences through text-based and voice-based interactions, evaluates candidate responses using AI, and generates detailed feedback reports to improve interview performance.

---

# Project Goals

- Generate resume-based interview questions.
- Simulate technical and behavioral interviews.
- Provide AI-generated feedback and improvement suggestions.
- Track interview history and performance progress.
- Support coding assessments.
- Enable voice-based interview experiences.

---

# In Scope

## 1. Authentication & User Management

### Features

- User Registration
- User Login
- JWT Authentication
- Refresh Token Support
- Password Reset
- User Profile Management

---

## 2. Resume Management

### Features

- Upload Resume (PDF/DOCX)
- Resume Storage
- Resume Parsing
- Skill Extraction
- Project Extraction
- Candidate Profile Generation

### AI Processing

Extract:

- Skills
- Technologies
- Projects
- Education
- Experience

---

## 3. AI Interview Generation

### Features

- Resume-Based Questions
- Role-Based Questions
- Technical Questions
- Behavioral Questions
- Coding Questions
- Dynamic Follow-Up Questions

### AI Provider

- Gemini 2.5 Flash

---

## 4. Interview Session Management

### Features

- Create Session
- Start Interview
- Question Delivery
- Answer Submission
- Session Progress Tracking
- Resume Incomplete Session
- Complete Interview

---

## 5. Voice Interview Module

### Features

- Text-To-Speech
- Speech-To-Text
- Voice Responses
- Voice-Based Interview Flow

### Integrations

- Murf AI
- AssemblyAI

---

## 6. Coding Assessment Module

### Features

- Coding Questions
- Code Editor
- Run Code
- Submit Code
- Execute Test Cases
- Score Coding Solutions

### Integration

- Judge0 API

---

## 7. Feedback & Evaluation

### Features

- Overall Interview Score
- Technical Score
- Communication Score
- Strength Analysis
- Weakness Analysis
- Improvement Suggestions

### AI Evaluation

- Gemini AI Evaluation Engine

---

## 8. Interview History

### Features

- Previous Interviews
- Feedback Reports
- Historical Scores
- Performance Tracking

---

## 9. Candidate Analytics Dashboard

### Features

- Interview Statistics
- Performance Trends
- Skill Progress Tracking
- Completion Metrics

---

# Non-Functional Requirements

## Security

- JWT Authentication
- Role-Based Access Control (Candidate Only)
- Password Encryption
- Input Validation
- Secure File Upload

---

## Performance

### Targets

- Support 100+ Concurrent Users
- Question Generation < 5 Seconds
- Feedback Generation < 10 Seconds
- Voice Latency < 5 Seconds

---

## Reliability

### Failure Handling

- Retry AI Requests (3 Attempts)
- Graceful AI Failure Recovery
- Session Recovery
- Resume Interview After Disconnect

---

## Data Management

### Storage

#### MySQL

Store:

- Users
- Resumes
- Interview Sessions
- Questions
- Answers
- Feedback
- History

#### File Storage

Store:

- Resume Files

### Retention Policy

- Resume Files: 90 Days
- Audio Files: Delete After Processing
- Interview History: Permanent

---

## Testing Strategy

### Backend

- JUnit
- Mockito
- Spring Boot Test

### Frontend

- React Testing Library

### Integration

- End-to-End API Testing

---

## Deployment

### Frontend

- Vercel

### Backend

- Render

### Database

- MySQL

### Containerization

- Docker

---

# Database Entities

## User

- id
- name
- email
- password
- role
- createdAt

## Resume

- id
- userId
- fileName
- filePath
- parsedContent
- uploadedAt

## CandidateProfile

- id
- userId
- skills
- projects
- education
- experience

## InterviewSession

- id
- userId
- resumeId
- role
- status
- startedAt
- endedAt
- durationMinutes

## InterviewQuestion

- id
- sessionId
- questionNumber
- category
- difficulty
- content

## InterviewAnswer

- id
- questionId
- answer
- score

## InterviewHistory

- id
- sessionId
- overallScore
- technicalScore
- communicationScore
- strengths
- weaknesses
- recommendations

## QuestionBank

- id
- category
- difficulty
- content
- source

---

# API Versioning

Base URL:

```text
/api/v1
```

---

# AI Services

## Gemini AI

Used For:

- Resume Parsing
- Question Generation
- Interview Evaluation
- Feedback Generation

## AssemblyAI

Used For:

- Speech-To-Text

## Murf AI

Used For:

- Text-To-Speech

## Judge0

Used For:

- Code Execution
- Coding Assessment

---

# Out of Scope

The following features are intentionally excluded:

- Admin Dashboard
- User Management Portal
- Platform Analytics for Administrators
- Multi-Tenant Architecture
- Enterprise Recruitment Integrations
- Live Human Interviewers
- Mobile Applications
- Campus Placement Management

---

# Future Enhancements

- AI-Powered Interview Coach
- Real-Time Follow-Up Question Generation
- Multi-Language Interviews
- Video Interview Support
- ATS Resume Scoring
- Personalized Learning Roadmaps
- Industry-Specific Interview Tracks