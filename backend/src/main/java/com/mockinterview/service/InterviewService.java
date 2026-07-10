package com.mockinterview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.dto.*;
import com.mockinterview.entity.*;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.mapper.InterviewMapper;
import com.mockinterview.repository.*;
import com.mockinterview.service.ai.AIProviderRouter;
import com.mockinterview.service.TextSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    /**
     * Neutral baseline used when no usable score signal exists (neither AI's holistic
     * call nor any per-answer evaluation succeeded). Deliberately not near-zero so an
     * outage never presents as a total failure.
     */
    private static final double NEUTRAL_BASELINE = 60.0;

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final CodingSubmissionRepository codingSubmissionRepository;
    private final InterviewHistoryRepository historyRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeService resumeService;
    private final UserRepository userRepository;
    private final InterviewMapper interviewMapper;
    private final ObjectMapper objectMapper;
    private final SpeechToTextService speechToTextService;
    private final TextToSpeechService textToSpeechService;
    private final AnswerValidationService answerValidationService;
    private final ScoringService scoringService;
    private final VoiceEvaluationService voiceEvaluationService;
    private final PersonalizedQuestionService personalizedQuestionService;
    private final FollowUpService followUpService;
    private final Judge0Service judge0Service;
    private final InterviewRecordingRepository interviewRecordingRepository;

    public InterviewService(InterviewRepository interviewRepository, QuestionRepository questionRepository,
                            AnswerRepository answerRepository, InterviewHistoryRepository historyRepository,
                            ResumeRepository resumeRepository, ResumeService resumeService,
                            UserRepository userRepository,
                            InterviewMapper interviewMapper, SpeechToTextService speechToTextService,
                            TextToSpeechService textToSpeechService,
                            AnswerValidationService answerValidationService,
                            ScoringService scoringService,
                            VoiceEvaluationService voiceEvaluationService,
                            PersonalizedQuestionService personalizedQuestionService,
                            FollowUpService followUpService,
                            CodeSubmissionRepository codeSubmissionRepository,
                            CodingSubmissionRepository codingSubmissionRepository,
                            Judge0Service judge0Service,
                            InterviewRecordingRepository interviewRecordingRepository) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.historyRepository = historyRepository;
        this.resumeRepository = resumeRepository;
        this.resumeService = resumeService;
        this.userRepository = userRepository;
        this.interviewMapper = interviewMapper;
        this.speechToTextService = speechToTextService;
        this.textToSpeechService = textToSpeechService;
        this.answerValidationService = answerValidationService;
        this.scoringService = scoringService;
        this.voiceEvaluationService = voiceEvaluationService;
        this.personalizedQuestionService = personalizedQuestionService;
        this.followUpService = followUpService;
        this.codeSubmissionRepository = codeSubmissionRepository;
        this.codingSubmissionRepository = codingSubmissionRepository;
        this.judge0Service = judge0Service;
        this.interviewRecordingRepository = interviewRecordingRepository;
        this.objectMapper = new ObjectMapper();
    }

    // ─────────────────────────────────────────────────────────
    // START INTERVIEW
    // ─────────────────────────────────────────────────────────

    @Transactional
    public InterviewResponse startInterview(Long userId, InterviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Resume resume = null;
        if (request.getResumeId() != null) {
            resume = resumeRepository.findById(request.getResumeId()).orElse(null);
        }

        String level = request.getInterviewLevel() != null ? request.getInterviewLevel().toUpperCase() : "STANDARD";
        int totalQ;
        String difficultyName;
        switch (level) {
            case "STARTER":
                totalQ = 7;
                difficultyName = "Starter";
                break;
            case "ADVANCED":
                totalQ = 25;
                difficultyName = "Advanced";
                break;
            case "STANDARD":
            default:
                totalQ = 15;
                difficultyName = "Standard";
                break;
        }

        String resumeText = resume != null ? resume.getResumeText() : null;
        String structuredSkills = resume != null ? resume.getStructuredSkills() : null;

        Integer targetCount = request.getTargetQuestionCount();
        if (targetCount == null) {
            targetCount = totalQ; // Fallback to legacy default
        }

        Interview interview = Interview.builder()
                .user(user)
                .interviewType(request.getRole())
                .interviewMode(request.getInterviewMode() != null ? request.getInterviewMode() : "RESUME")
                .codingLanguage(request.getCodingLanguage())
                .selectedInterests(request.getSelectedInterests())
                .difficulty(difficultyName)
                .status("in_progress")
                .targetQuestionCount(targetCount)
                .totalQuestions(targetCount) // totalQuestions will be the actual total
                .currentQuestion(1)
                .resumeText(resumeText)
                .resumeId(request.getResumeId())
                .voiceEnabled(request.getVoiceEnabled() != null ? request.getVoiceEnabled() : true)
                .voiceName(request.getVoiceName())
                .voiceSpeed(request.getVoiceSpeed() != null ? request.getVoiceSpeed() : 1.0)
                .build();
        interview = interviewRepository.save(interview);

        // Persist the candidate's selected Murf voice/style for this session
        interview.setVoiceId(request.getVoiceId());
        interview.setStyle(request.getStyle());
        interview = interviewRepository.save(interview);

        // ── Question 1: dynamic, role- and resume-aware intro ─
        String introText = personalizedQuestionService.generateIntroQuestion(request.getRole(), structuredSkills);
        Question introQ = Question.builder()
                .interview(interview)
                .questionText(introText)
                .type("behavioral")
                .isCodeQuestion(false)
                .generatedByAI(true)
                .build();
        questionRepository.save(introQ);

        // ── Questions 2–N: AI-generated, resume-personalized (Initial Batch) ──
        // Generate all questions upfront based on difficulty (not limited by initial small batch)
        int batchSize = targetCount - 1; // Minus the intro already saved
        generateBatchForInterview(interview, request.getRole(), resumeText, structuredSkills, batchSize, level, userId, request.getResumeId());

        // ── Assign stable sequence ordering (enables adaptive follow-up insertion) ──
        assignSequences(interview.getId());
        
        // ── Build response ────────────────────────────────────
        List<Question> savedQuestions = getOrderedQuestions(interview.getId());
        InterviewResponse response = interviewMapper.toDTO(interview);
        // Generate dynamic welcome introduction
        String candidateName = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : "Candidate";
        String welcomeText = String.format(
            "Hello %s. Welcome to your %s mock interview. Today's interview will contain %d questions. Please answer clearly and confidently. Let's begin.",
            candidateName,
            request.getRole(),
            totalQ
        );

        response.setGreeting(welcomeText);

        List<QuestionDTO> questionDTOs = toQuestionDTOs(savedQuestions);
        response.setQuestions(questionDTOs);
        response.setMessages(List.of());

        if (!questionDTOs.isEmpty()) {
            response.setQuestion(questionDTOs.get(0));
        }

        // Generate spoken greeting audio via Murf TTS (null if unavailable -> UI continues silently)
        if (Boolean.TRUE.equals(interview.getVoiceEnabled())) {
            String greetingAudio = textToSpeechService.synthesizeSpeech(welcomeText, buildVoiceOptions(interview));
            response.setAudio(greetingAudio);
            if (greetingAudio != null) {
                interview.setLastAudio(greetingAudio);
                interviewRepository.save(interview);
            }
        }

        return response;
    }



    // ─────────────────────────────────────────────────────────
    // BATCH GENERATOR (Queue Manager)
    // ─────────────────────────────────────────────────────────
    
    private void generateBatchForInterview(Interview interview, String role, String resumeText, String structuredSkills, int batchSize, String level, Long userId, Long resumeId) {
        if (batchSize <= 0) return;
        
        String mode = interview.getInterviewMode() != null ? interview.getInterviewMode() : "RESUME";
        String lvl = level != null ? level.toUpperCase() : "STANDARD";
        
        int hr = 0, tech = 0, proj = 0, code = 0, interest = 0;
        
        if (mode.equals("CODING_INTERVIEW")) {
            // Coding Interview Mode Optimization
            switch (lvl) {
                case "STARTER":
                    code = 5; tech = 2; break; // 2 Conceptual -> tech
                case "BEGINNER":
                    code = 8; tech = 2; break;
                case "STANDARD":
                    code = 12; tech = 3; break;
                case "INTERMEDIATE":
                    code = 18; tech = 2; break; // 2 System Design -> tech
                case "ADVANCED":
                    code = 20; tech = 5; break;
                default: // Fallback
                    code = 12; tech = 3; break;
            }
        } else if (mode.equals("HR") || mode.equals("INTEREST_BASED") || mode.equals("HYBRID")) {
            // Preserve logic for other modes or scale based on batchSize
            if (mode.equals("HR")) {
                hr = batchSize;
            } else if (mode.equals("INTEREST_BASED")) {
                interest = batchSize;
            } else if (mode.equals("HYBRID")) {
                proj = (int) Math.round(batchSize * 0.40);
                interest = (int) Math.round(batchSize * 0.30);
                tech = (int) Math.round(batchSize * 0.20);
                hr = batchSize - proj - interest - tech;
                if (hr < 0) hr = 0;
            }
        } else {
            // Normal Interviews (Technical/Mock/Resume)
            switch (lvl) {
                case "STARTER":
                    hr = 3; tech = 3; code = 1; break;
                case "BEGINNER":
                    hr = 3; tech = 4; code = 3; break;
                case "STANDARD":
                    hr = 4; tech = 6; code = 5; break;
                case "INTERMEDIATE":
                    hr = 4; tech = 8; code = 8; break;
                case "ADVANCED":
                    hr = 5; tech = 10; code = 10; break;
                default:
                    hr = 4; tech = 6; code = 5; break;
            }
        }
        
        // Update target count based on actual generation
        int total = hr + tech + proj + code + interest;
        interview.setTargetQuestionCount(total);
        interviewRepository.save(interview);
        
        personalizedQuestionService.generateAndSaveAIQuestions(
                interview, role, resumeText, structuredSkills,
                hr, tech, proj, code, interest, level, userId, resumeId);
    }

    // ─────────────────────────────────────────────────────────
    // SUBMIT ANSWER
    // ─────────────────────────────────────────────────────────

    @Transactional
    public AnswerResponse submitAnswer(Long userId, Long interviewId, AnswerRequest request) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));

        if ("completed".equals(interview.getStatus())) {
            throw new IllegalArgumentException("Interview is already completed");
        }

        List<Question> questions = getOrderedQuestions(interview.getId());
        if (questions.isEmpty()) {
            throw new IllegalStateException("No questions found for this interview");
        }

        int answeredSeq = interview.getCurrentQuestion();
        if (answeredSeq < 1 || answeredSeq > questions.size()) {
            throw new IllegalStateException("No more questions available.");
        }
        Question question = questions.get(answeredSeq - 1);

        // 1) Validate the Answer
        answerValidationService.validateAnswer(
                request.getAnswerText() != null ? request.getAnswerText() : request.getCode(), question.getType());

        // 2) Save Candidate's Answer
        String answerText = request.getCode() != null && !request.getCode().isBlank()
                ? request.getCode()
                : request.getAnswerText();

        Answer answer = Answer.builder()
                .question(question)
                .answerText(answerText)
                .codeLanguage(request.getLanguage())
                .responseTimeSeconds(request.getResponseTimeSeconds())
                .difficultyLevel(question.getDifficulty())
                .build();
        answerRepository.save(answer);

        // 2b) Real code execution via Judge0 (spec #10) — runs the candidate's code against
        // this question's visible + hidden test cases and persists the metrics. Falls back
        // transparently to the AI evaluation below when Judge0 is unavailable.
        Judge0Result judge0Result = null;
        if (request.getCode() != null && !request.getCode().isBlank()) {
            judge0Result = runJudge0(interview, question, answerText, request.getLanguage());
            if (judge0Result != null && judge0Result.getTotalTests() > 0) {
                answer.setCodeExecutionResult(String.format(
                        "passed %d/%d test cases (%s)",
                        judge0Result.getPassedTests(), judge0Result.getTotalTests(),
                        judge0Result.isPassed() ? "PASS" : "FAIL"));
            }
        }

        // 3) Evaluate Answer via Voice or Text Scoring Service
        if (request.getAudioDurationSeconds() != null && request.getAudioDurationSeconds() > 0) {
            voiceEvaluationService.evaluateVoiceAnswer(answer, question, request);
        } else {
            scoringService.evaluateAnswer(answer, question, request, judge0Result);
        }
        answerRepository.save(answer);

        // 4) Adaptive engine (#5): update running score + adapted difficulty
        updateAdaptiveState(interview, answer);

        // 5) Dynamic follow-up questions (#4): analyse the answer and probe deeper.
        //    Persisted as follow-ups inserted immediately after this question.
        int alreadyGenerated = countFollowUps(interview.getId());
        String adaptedWord = interview.getAdaptedDifficulty() != null
                ? interview.getAdaptedDifficulty() : interview.getDifficulty();
        String followUpLevelWord = levelWordFor(adaptedWord);
        String resumeContext = buildFollowUpResumeContext(interview);
        
        List<Question> newFollowUps = new java.util.ArrayList<>();
        // Only generate follow-ups if the score is below 70
        if (answer.getEvaluationScore() != null && answer.getEvaluationScore() < 70.0) {
            newFollowUps = followUpService.generateFollowUps(
                    interview, question, answer, interview.getInterviewType(),
                    followUpLevelWord, resumeContext, alreadyGenerated);
            insertFollowUpsAfter(question, newFollowUps);
        }

        // 6) Determine the next question (a follow-up if one was just generated, else advance)
        List<Question> updated = getOrderedQuestions(interview.getId());
        int totalNow = updated.size();
        int nextSeq = answeredSeq + 1;
        int target = interview.getTargetQuestionCount() != null ? interview.getTargetQuestionCount() : 15;
        boolean isComplete = answeredSeq >= target;

        AnswerResponse response = new AnswerResponse();
        if (judge0Result != null) {
            response.setEvaluation(judge0Result); // matches the required stdout/stderr/passed/... shape
        }

        // TERMINATION CHECK: Stop if we've answered exactly (or more than) the target count.
        if (answeredSeq >= target) {
            response.setComplete(true);
            String closingMsg = "Thank you for completing the interview! Generating your detailed feedback report...";
            response.setMessage(closingMsg);
            if (Boolean.TRUE.equals(interview.getVoiceEnabled())) {
                String closingAudio = textToSpeechService.synthesizeSpeech(closingMsg, buildVoiceOptions(interview));
                response.setAudio(closingAudio);
            }
            interview.setTotalQuestions(totalNow);
            interviewRepository.save(interview);
            endInterview(userId, interviewId);
            return response;
        }

        // QUEUE MANAGER: Ensure we have the next question
        if (totalNow < nextSeq) {
            int remaining = target - answeredSeq; // How many answers are left to reach the target
            int batchSize = Math.min(3, remaining); // Generate up to 3 at a time
            
            generateBatchForInterview(interview, interview.getInterviewType(), interview.getResumeText(), null, batchSize, adaptedWord, userId, interview.getResumeId());
            
            updated = getOrderedQuestions(interview.getId());
            totalNow = updated.size();
            
            // FALLBACK GUARANTEE: If AI completely failed to generate enough questions, force fallback!
            if (totalNow < nextSeq) {
                int needed = Math.min(3, target - totalNow);
                java.util.Set<String> seen = updated.stream()
                        .map(q -> com.mockinterview.service.TextSimilarity.normalize(q.getQuestionText()))
                        .collect(Collectors.toSet());
                personalizedQuestionService.forceSaveFallbackQuestions(interview, needed, seen);
                updated = getOrderedQuestions(interview.getId());
                totalNow = updated.size();
            }
        }

        // Prepare the next question payload
        response.setComplete(false);
        response.setCurrentQuestion(nextSeq);
        response.setTotalQuestions(totalNow);
        response.setTargetQuestions(target);

        Question nextQ = updated.get(nextSeq - 1);
        QuestionDTO nextQDto = toQuestionDTO(nextQ);
        response.setQuestion(nextQDto);
        String aiReaction = newFollowUps.isEmpty()
                ? "Good answer! Let's move to the next question."
                : "Great — let me dig a little deeper on that.";
        response.setResponse(aiReaction);
        response.setFollowUps(newFollowUps.stream().map(this::toQuestionDTO).collect(Collectors.toList()));

        if (Boolean.TRUE.equals(interview.getVoiceEnabled())) {
            // Build TTS-friendly text — for coding questions include full problem narration
            String ttsText;
            if (Boolean.TRUE.equals(nextQ.getIsCodeQuestion())) {
                StringBuilder codeTts = new StringBuilder();
                codeTts.append(aiReaction).append(" ");
                codeTts.append("Your next question is a coding challenge. ");
                if (nextQ.getTitle() != null && !nextQ.getTitle().isBlank()) {
                    codeTts.append("Problem: ").append(nextQ.getTitle()).append(". ");
                }
                if (nextQ.getProblemDescription() != null && !nextQ.getProblemDescription().isBlank()) {
                    codeTts.append(nextQ.getProblemDescription()).append(" ");
                } else {
                    codeTts.append(nextQ.getQuestionText()).append(" ");
                }
                if (nextQ.getConstraints() != null && !nextQ.getConstraints().isBlank()) {
                    codeTts.append("Constraints: ").append(nextQ.getConstraints());
                }
                ttsText = codeTts.toString();
            } else {
                ttsText = aiReaction + " " + nextQDto.getText();
            }
            String nextAudio = textToSpeechService.synthesizeSpeech(ttsText, buildVoiceOptions(interview));
            response.setAudio(nextAudio);
            if (nextAudio != null) {
                interview.setLastAudio(nextAudio);
            }
        }
        interview.setCurrentQuestion(nextSeq);
        interview.setTotalQuestions(totalNow);
        interviewRepository.save(interview);

        return response;
    }



    // ─────────────────────────────────────────────────────────
    // END INTERVIEW — Generate Feedback
    // ─────────────────────────────────────────────────────────

    @Transactional
    public FeedbackResponse endInterview(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));

        // Don't re-generate if already completed
        if ("completed".equals(interview.getStatus()) && interview.getFeedback() != null) {
            return buildExistingFeedbackResponse(interview);
        }

        interview.setStatus("completed");
        interview.setCompletedAt(LocalDateTime.now());

        // ── Gather all Q&A pairs for the prompt ──────────────
        List<Question> questions = questionRepository.findByInterviewId(interviewId);
        String qaContext = buildQAContext(questions);

        // ── 1) Holistic AI evaluation (may be null on failure; handled gracefully) ──
        String aiResponse = scoringService.generateFinalFeedback(interview.getInterviewType(), qaContext);
        Map<String, Object> aiMap = parseAIJson(interviewId, aiResponse);

        // ── 2) Aggregate the per-answer structured scores (the real signal) ──
        AnswerAggregates agg = computeAnswerAggregates(questions);

        // ── 3) Build a normalized feedback payload with exactly the 5 keys the UI expects ──
        String normalizedFeedback = buildNormalizedFeedbackJson(aiMap, agg);
        interview.setFeedback(normalizedFeedback);

        // ── 4) Overall score: prefer AI holistic, else per-answer avg, else neutral ──
        double overall = pickOverall(aiMap, agg);
        interview.setScore(overall);

        // ── 5) Persist history (strengths/weaknesses for analytics) ──
        persistHistory(interview, overall, aiMap);

        interviewRepository.save(interview);

        return buildFeedbackResponse(interview, questions);
    }

    // ─────────────────────────────────────────────────────────
    // GET INTERVIEW
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InterviewResponse getInterview(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));

        InterviewResponse response = interviewMapper.toDTO(interview);

        // Ensure messages is never null (frontend does .filter() on it)
        if (response.getMessages() == null) {
            response.setMessages(List.of());
        }

        // Populate parsed feedback JSON
        if (interview.getFeedback() != null) {
            try {
                response.setFeedback(objectMapper.readValue(interview.getFeedback(), Map.class));
            } catch (Exception e) {
                response.setFeedback(interview.getFeedback());
            }
        }

        return response;
    }

    // ─────────────────────────────────────────────────────────
    // VOICE / AUDIO
    // ─────────────────────────────────────────────────────────

    public java.util.Map<String, Object> transcribeAudio(org.springframework.web.multipart.MultipartFile audio) {
        return speechToTextService.transcribeAudioWithMetadata(audio);
    }

    @Transactional
    public AnswerResponse submitVoiceAnswer(Long userId, Long interviewId, org.springframework.web.multipart.MultipartFile audio) {
        java.util.Map<String, Object> transcriptionData = transcribeAudio(audio);
        String transcribedText = (String) transcriptionData.getOrDefault("text", "");
        Integer duration = (Integer) transcriptionData.getOrDefault("duration", 0);
        
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
        List<Question> questions = getOrderedQuestions(interviewId);
        int currentSeq = interview.getCurrentQuestion();
        
        if (currentSeq >= 1 && currentSeq <= questions.size()) {
            Question question = questions.get(currentSeq - 1);
            
            try {
                java.io.File dir = new java.io.File("uploads/recordings");
                if (!dir.exists()) dir.mkdirs();
                
                String filename = "recording_" + interviewId + "_" + question.getId() + "_" + System.currentTimeMillis() + ".webm";
                java.nio.file.Path filepath = java.nio.file.Paths.get("uploads/recordings", filename);
                java.nio.file.Files.copy(audio.getInputStream(), filepath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                InterviewRecording recording = InterviewRecording.builder()
                        .interviewId(interviewId)
                        .questionId(question.getId())
                        .userId(userId)
                        .audioUrl("/api/audio/" + filename)
                        .fileSizeBytes(audio.getSize())
                        .transcript(transcribedText)
                        .build();
                interviewRecordingRepository.save(recording);
            } catch (Exception e) {
                log.error("Failed to save audio recording: " + e.getMessage());
            }
        }
        
        int fillerWords = 0;
        if (transcribedText != null && !transcribedText.isBlank()) {
            String lower = transcribedText.toLowerCase();
            String[] tokens = lower.split("\\W+");
            for (String t : tokens) {
                if (t.equals("um") || t.equals("uh") || t.equals("like") || t.equals("you know") || t.equals("basically") || t.equals("actually")) {
                    fillerWords++;
                }
            }
        }
        
        double speed = 0.0;
        if (duration != null && duration > 0 && transcribedText != null) {
            int wordCount = transcribedText.split("\\s+").length;
            speed = (wordCount / (double) duration) * 60.0;
        }
        
        AnswerRequest request = new AnswerRequest();
        request.setAnswerText(transcribedText);
        request.setAudioDurationSeconds(duration);
        request.setFillerWordsCount(fillerWords);
        request.setSpeakingSpeed(speed);
        return submitAnswer(userId, interviewId, request);
    }

    public String speakText(String text) {
        return textToSpeechService.synthesizeSpeech(text);
    }

    public java.util.Map<String, String> generateWelcomeIntroduction(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
        
        String candidateName = interview.getUser().getFullName();
        if (candidateName == null || candidateName.isBlank()) {
            candidateName = "Candidate";
        }

        String welcomeText = String.format(
            "Hello %s. Welcome to your %s mock interview. Today's interview will contain %d questions. Please answer clearly and confidently. Let's begin.",
            candidateName,
            interview.getInterviewType(),
            interview.getTotalQuestions() != null ? interview.getTotalQuestions() : 5
        );

        String audioUrl = speakText(welcomeText);

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("text", welcomeText);
        response.put("audio", audioUrl != null ? audioUrl : "");
        return response;
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────

    private String buildQAContext(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return "No questions answered.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            sb.append("\n--- Question ").append(i + 1).append(" ---\n");
            sb.append("Q: ").append(q.getQuestionText()).append("\n");

            // Get the most recent answer for this question
            if (q.getAnswers() != null && !q.getAnswers().isEmpty()) {
                Answer latestAnswer = q.getAnswers().get(q.getAnswers().size() - 1);
                String answerText = latestAnswer.getAnswerText();
                sb.append("A: ").append(answerText != null && !answerText.isBlank()
                        ? answerText.substring(0, Math.min(answerText.length(), 500))
                        : "[No answer provided]").append("\n");
                if (latestAnswer.getEvaluationScore() != null) {
                    sb.append("Score: ").append(latestAnswer.getEvaluationScore()).append("/100\n");
                }
                if (latestAnswer.getCodeExecutionResult() != null
                        && !latestAnswer.getCodeExecutionResult().isBlank()) {
                    sb.append("Code execution (Judge0): ")
                      .append(latestAnswer.getCodeExecutionResult()).append("\n");
                }
            } else {
                sb.append("A: [No answer provided]\n");
            }
        }
        return sb.toString();
    }

    // ── Feedback aggregation helpers ──

    /** Per-answer score averages, used to reconstruct a realistic feedback payload. */
    private static class AnswerAggregates {
        Double overall;
        Double technical;
        Double communication;
        Double problemSolving;
        Double codeQuality;
        Double project;
        Double confidence;
    }

    /**
     * Defensively parse AI's final-feedback response into a Map. Returns {@code null}
     * (never throws) when the call failed or the payload is unparseable, so the caller can
     * fall back to the per-answer aggregates instead of collapsing to a hardcoded score.
     */
    private Map<String, Object> parseAIJson(Long interviewId, String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("AI final-feedback returned empty/null response; scores will be derived from per-answer evaluations.");
            return null;
        }
        try {
            String json = extractJson(raw);
            if (json == null || json.isBlank()) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) objectMapper.readValue(json, Map.class);
            log.info("Parsed AI final feedback: {}", json.length() > 500 ? json.substring(0, 500) + "..." : json);
            return m;
        } catch (Exception e) {
            log.error("AI final-feedback parse failed for interview {}", interviewId, e);
            return null;
        }
    }

    private AnswerAggregates computeAnswerAggregates(List<Question> questions) {
        double oSum = 0, tSum = 0, cSum = 0, pSum = 0, kSum = 0, prSum = 0, cfSum = 0;
        int oN = 0, tN = 0, cN = 0, pN = 0, kN = 0, prN = 0, cfN = 0;
        for (Question q : questions) {
            if (q.getAnswers() == null) continue;
            for (Answer a : q.getAnswers()) {
                if (a.getEvaluationScore() != null)   { oSum += a.getEvaluationScore(); oN++; }
                if (a.getTechnicalScore() != null)    { tSum += a.getTechnicalScore();    tN++; }
                if (a.getCommunicationScore() != null) { cSum += a.getCommunicationScore(); cN++; }
                if (a.getProblemSolvingScore() != null){ pSum += a.getProblemSolvingScore();pN++; }
                if (a.getCodeQualityScore() != null)   { kSum += a.getCodeQualityScore();   kN++; }
                if (a.getProjectScore() != null)       { prSum += a.getProjectScore();      prN++; }
                if (a.getConfidenceScore() != null)    { cfSum += a.getConfidenceScore();   cfN++; }
            }
        }
        AnswerAggregates agg = new AnswerAggregates();
        agg.overall        = oN > 0 ? (double) Math.round(oSum / oN) : null;
        agg.technical      = tN > 0 ? (double) Math.round(tSum / tN) : null;
        agg.communication  = cN > 0 ? (double) Math.round(cSum / cN) : null;
        agg.problemSolving = pN > 0 ? (double) Math.round(pSum / pN) : null;
        agg.codeQuality    = kN > 0 ? (double) Math.round(kSum / kN) : null;
        agg.project        = prN > 0 ? (double) Math.round(prSum / prN) : null;
        agg.confidence     = cfN > 0 ? (double) Math.round(cfSum / cfN) : null;
        return agg;
    }

    /**
     * Build the feedback payload the UI consumes. It ALWAYS contains {@code overallScore}
     * and a {@code categoryScores} object with the five keys the frontend reads
     * (communicationSkills, technicalKnowledge, problemSolving, codeQuality, confidence).
     * Each category prefers AI's holistic score, then the per-answer aggregate, then a
     * neutral baseline — so the result is realistic when AI worked and sane otherwise.
     */
    private String buildNormalizedFeedbackJson(Map<String, Object> AI, AnswerAggregates agg) {
        @SuppressWarnings("unchecked")
        Map<String, Object> aiCats = (AI != null && AI.get("categoryScores") instanceof Map)
                ? (Map<String, Object>) AI.get("categoryScores")
                : new LinkedHashMap<>();

        java.util.function.Function<String, Double> aiScore = key -> {
            Object cat = aiCats.get(key);
            if (cat instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapCat = (Map<String, Object>) cat;
                return toDouble(mapCat.get("score"));
            }
            return null;
        };

        double comm = firstNonNull(agg.communication, NEUTRAL_BASELINE);
        double tech = firstNonNull(agg.technical,     NEUTRAL_BASELINE);
        double proj = firstNonNull(agg.project,       NEUTRAL_BASELINE);
        double code = firstNonNull(agg.codeQuality,   NEUTRAL_BASELINE);
        double conf = firstNonNull(agg.confidence,    NEUTRAL_BASELINE);

        double exactWeightedOverall = clamp(
            (comm * 0.20) + (tech * 0.30) + (proj * 0.20) + (code * 0.20) + (conf * 0.10)
        );

        Map<String, Object> categoryScores = new LinkedHashMap<>();
        categoryScores.put("communicationScore", scoreNode(round1(comm), catComment(aiCats, "communicationScore", "Fluency, Clarity, and Grammar.")));
        categoryScores.put("technicalScore",  scoreNode(round1(tech), catComment(aiCats, "technicalScore", "Accuracy and Depth of Technical Concepts.")));
        categoryScores.put("problemSolvingScore", scoreNode(round1(proj), catComment(aiCats, "problemSolvingScore", "Analytical ability and Logic.")));
        categoryScores.put("codingScore",         scoreNode(round1(code), catComment(aiCats, "codingScore", "Correctness, Efficiency, and Style.")));
        categoryScores.put("confidenceScore",     scoreNode(round1(conf), catComment(aiCats, "confidenceScore", "Assertiveness and Delivery.")));

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("overallScore", round1(exactWeightedOverall));
        feedback.put("categoryScores", categoryScores);
        feedback.put("strengths", pickList(AI, "strengths"));
        feedback.put("areasOfImprovement", pickList(AI, "areasOfImprovement", "weaknesses"));
        feedback.put("missedConcepts", pickList(AI, "missedConcepts"));
        feedback.put("recommendedLearningTopics", pickList(AI, "recommendedLearningTopics", "recommendedTopics", "recommendations"));
        feedback.put("weakConcepts", pickList(AI, "weakConcepts"));
        feedback.put("strongConcepts", pickList(AI, "strongConcepts"));
        feedback.put("codingPerformance", pickString(AI, "codingPerformance", null));
        feedback.put("careerGuidance", pickString(AI, "careerGuidance", null));
        feedback.put("hiringRecommendation", pickString(AI, "hiringRecommendation", "Undetermined"));
        feedback.put("category", pickString(AI, "category", "Average"));
        // Flag whether a real evaluation ran. When AI's holistic call failed, the
        // scores below are the NEUTRAL_BASELINE (60) placeholders, not a real grade — the
        // UI must surface this instead of presenting a confident 60/100.
        feedback.put("evaluated", AI != null);
        String assessmentFallback = (AI != null)
                ? "Overall interview performance was assessed from the candidate's answers."
                : "Scoring could not be completed — the AI evaluation service was unavailable. "
                  + "The scores shown are neutral placeholders and do not reflect actual performance.";
        feedback.put("interviewSummary", pickString(AI, "interviewSummary", pickString(AI, "finalAssessment", assessmentFallback)));
        try {
            return objectMapper.writeValueAsString(feedback);
        } catch (JsonProcessingException e) {
            return "{\"overallScore\":" + round1(exactWeightedOverall) + ",\"categoryScores\":{},\"strengths\":[],\"areasOfImprovement\":[],\"interviewSummary\":\"Interview completed.\"}";
        }
    }

    /** Reconstruct the FeedbackResponse DTO from the persisted interview + question details. */
    private FeedbackResponse buildFeedbackResponse(Interview interview, List<Question> questions) {
        FeedbackResponse response = new FeedbackResponse();
        response.setInterviewId(interview.getId().toString());
        response.setOverallScore(interview.getScore());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fb = (Map<String, Object>) objectMapper.readValue(interview.getFeedback(), Map.class);
            response.setFeedback(fb);
            Object ev = fb.get("evaluated");
            response.setEvaluated(ev instanceof Boolean ? (Boolean) ev : null);
        } catch (Exception e) {
            response.setFeedback(interview.getFeedback());
        }

        List<QuestionFeedbackDTO> qaFeedback = new ArrayList<>();
        for (Question q : questions) {
            QuestionFeedbackDTO dto = QuestionFeedbackDTO.builder()
                    .questionText(q.getQuestionText())
                    .type(q.getType())
                    .difficulty(q.getDifficulty())
                    .idealAnswer(q.getExpectedAnswer())
                    .explanation(q.getExplanation())
                    .build();

            if (q.getAnswers() != null) {
                q.getAnswers().stream().findFirst().ifPresent(ans -> {
                    dto.setCandidateAnswer(ans.getAnswerText());
                    dto.setScore(ans.getEvaluationScore());
                    dto.setFeedback(ans.getFeedback());
                    dto.setImprovementSuggestions(ans.getImprovementSuggestions());
                    dto.setAnswerComparison(ans.getAnswerComparison());
                    dto.setTechnicalScore(ans.getTechnicalScore());
                    dto.setCommunicationScore(ans.getCommunicationScore());
                    dto.setProblemSolvingScore(ans.getProblemSolvingScore());
                    dto.setCodeQualityScore(ans.getCodeQualityScore());
                    dto.setProjectScore(ans.getProjectScore());
                    dto.setConfidenceScore(ans.getConfidenceScore());
                    dto.setStrengths(ans.getStrengths());
                    dto.setWeaknesses(ans.getWeaknesses());
                    dto.setRecommendations(ans.getRecommendations());
                    dto.setSpeakingSpeed(ans.getSpeakingSpeed());
                    dto.setFluencyScore(ans.getFluencyScore());
                    dto.setFillerWordsCount(ans.getFillerWordsCount());
                });
            }
            
            try {
                com.mockinterview.entity.InterviewRecording recording = interviewRecordingRepository.findByQuestionId(q.getId());
                if (recording != null) {
                    dto.setRecordingUrl(recording.getAudioUrl());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch recording for question {}", q.getId());
            }

            // Fetch coding submission details if it's a coding question
            if (Boolean.TRUE.equals(q.getIsCodeQuestion())) {
                try {
                    // Get latest submission for this question
                    List<CodeSubmission> subs = 
                            codeSubmissionRepository.findByInterviewId(interview.getId()).stream()
                            .sorted(Comparator.comparing(CodeSubmission::getCreatedAt).reversed())
                            .collect(Collectors.toList());

                    if (!subs.isEmpty()) {
                        CodeSubmission latestSub = subs.get(0);
                        dto.setCodeLanguage(latestSub.getLanguage());
                        dto.setCodeSnippet(latestSub.getCode());
                        dto.setExecutionStatus(latestSub.getStatus());

                        // Assuming there is a OneToOne mapping to result or we query result repo
                        // For simplicity, we just fetch from result repo if we have it, or maybe it's in the DB.
                        // We will skip actual test case matching here if it's too complex and just provide the code snippet.
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch coding submission for question {}", q.getId(), e);
                }
            }

            qaFeedback.add(dto);
        }
        response.setQuestions(qaFeedback);
        return response;
    }

    // ── small scoring utilities ──

    private static Map<String, Object> scoreNode(double score, String comment) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("score", score);
        node.put("comment", comment);
        return node;
    }

    private static String catComment(Map<String, Object> aiCats, String key, String fallback) {
        Object cat = aiCats.get(key);
        if (cat instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapCat = (Map<String, Object>) cat;
            Object c = mapCat.get("comment");
            if (c instanceof String s && !s.isBlank()) return s;
        }
        return fallback;
    }

    private static double firstNonNull(Double... values) {
        for (Double v : values) {
            if (v != null) return v;
        }
        return NEUTRAL_BASELINE;
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static double round1(double d) {
        return Math.round(d * 10.0) / 10.0;
    }

    private static double clamp(double d) {
        return Math.max(0.0, Math.min(100.0, d));
    }

    private double pickOverall(Map<String, Object> AI, AnswerAggregates agg) {
        if (agg != null) {
            double comm = firstNonNull(agg.communication, NEUTRAL_BASELINE);
            double tech = firstNonNull(agg.technical,     NEUTRAL_BASELINE);
            double proj = firstNonNull(agg.project,       NEUTRAL_BASELINE);
            double code = firstNonNull(agg.codeQuality,   NEUTRAL_BASELINE);
            double conf = firstNonNull(agg.confidence,    NEUTRAL_BASELINE);
            return clamp((comm * 0.20) + (tech * 0.30) + (proj * 0.20) + (code * 0.20) + (conf * 0.10));
        }
        return NEUTRAL_BASELINE;
    }

    private List<String> pickList(Map<String, Object> AI, String... keys) {
        if (AI == null) return List.of();
        for (String key : keys) {
            Object val = AI.get(key);
            if (val instanceof List<?> list && !list.isEmpty()) {
                return list.stream().map(String::valueOf).collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private String pickString(Map<String, Object> AI, String key, String fallback) {
        if (AI != null) {
            Object val = AI.get(key);
            if (val instanceof String s && !s.isBlank()) return s;
        }
        return fallback;
    }

    private String listToString(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(", ", list);
    }

    private void persistHistory(Interview interview, double overall, Map<String, Object> AI) {
        String strengths = listToString(pickList(AI, "strengths"));
        String improvements = listToString(pickList(AI, "areasOfImprovement", "weaknesses", "recommendations"));
        historyRepository.findByInterviewId(interview.getId()).ifPresentOrElse(
            existing -> {
                existing.setTotalScore(overall);
                existing.setStrengths(strengths);
                existing.setImprovements(improvements);
                existing.setStrongSkills(strengths);
                existing.setWeakSkills(improvements);
                historyRepository.save(existing);
            },
            () -> historyRepository.save(InterviewHistory.builder()
                    .user(interview.getUser()).interview(interview)
                    .totalScore(overall).strengths(strengths)
                    .improvements(improvements).strongSkills(strengths).weakSkills(improvements).build())
        );
    }

    private FeedbackResponse buildExistingFeedbackResponse(Interview interview) {
        FeedbackResponse existing = new FeedbackResponse();
        existing.setInterviewId(interview.getId().toString());
        existing.setOverallScore(interview.getScore());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fb = (Map<String, Object>) objectMapper.readValue(interview.getFeedback(), Map.class);
            existing.setFeedback(fb);
            Object ev = fb.get("evaluated");
            existing.setEvaluated(ev instanceof Boolean ? (Boolean) ev : null);
        } catch (Exception e) {
            existing.setFeedback(interview.getFeedback());
        }
        return existing;
    }

    private MurfVoiceOptions buildVoiceOptions(Interview interview) {
        Double speed = interview.getVoiceSpeed();
        Integer rate = null;
        if (speed != null) {
            int r = (int) Math.round((speed - 1.0) * 100);
            r = Math.max(-50, Math.min(50, r));
            rate = r;
        }
        return MurfVoiceOptions.builder()
                .voiceId(interview.getVoiceId())
                .style(interview.getStyle())
                .rate(rate)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // ADAPTIVE + FOLLOW-UP HELPERS (spec #4, #5)
    // ─────────────────────────────────────────────────────────

    /** Questions for an interview, ordered by their stable sequence. */
    private List<Question> getOrderedQuestions(Long interviewId) {
        List<Question> qs = questionRepository.findByInterviewId(interviewId);
        qs.sort(Comparator.comparingInt(q ->
                q.getSequence() != null ? q.getSequence() : (q.getId() != null ? q.getId().intValue() : 0)));
        return qs;
    }

    /** Assign contiguous 1-based sequences to an interview's questions (id order). */
    private void assignSequences(Long interviewId) {
        List<Question> qs = questionRepository.findByInterviewId(interviewId);
        qs.sort(Comparator.comparingLong(q -> q.getId() != null ? q.getId() : 0L));
        for (int i = 0; i < qs.size(); i++) {
            qs.get(i).setSequence(i + 1);
        }
        questionRepository.saveAll(qs);
    }

    private int countFollowUps(Long interviewId) {
        return (int) questionRepository.findByInterviewId(interviewId).stream()
                .filter(q -> Boolean.TRUE.equals(q.getIsFollowUp()))
                .count();
    }

    /**
     * Insert follow-ups immediately after their parent in the sequence, shifting later
     * questions up so the interview flows naturally (the next request returns the follow-up).
     */
    private void insertFollowUpsAfter(Question parent, List<Question> followUps) {
        if (followUps == null || followUps.isEmpty()) return;
        Integer parentSeq = parent.getSequence() != null ? parent.getSequence() : 0;
        int k = followUps.size();
        List<Question> all = questionRepository.findByInterviewId(parent.getInterview().getId());
        for (Question q : all) {
            if (q.getSequence() != null && q.getSequence() > parentSeq) {
                q.setSequence(q.getSequence() + k);
                questionRepository.save(q);
            }
        }
        int s = parentSeq + 1;
        for (Question f : followUps) {
            f.setSequence(s++);
            questionRepository.save(f);
        }
    }

    /**
     * Update the rolling running score and the adapted difficulty. Strong recent
     * performance escalates the difficulty; weak performance drops it.
     */
    private void updateAdaptiveState(Interview interview, Answer answer) {
        if (answer.getEvaluationScore() == null) return;
        
        double baseScore = answer.getEvaluationScore();
        double confidence = answer.getConfidenceScore() != null ? answer.getConfidenceScore() : 60.0;
        int time = answer.getResponseTimeSeconds() != null ? answer.getResponseTimeSeconds() : 60;
        
        // Time penalty/bonus
        double timeMod = 0;
        if (time < 30) timeMod = 5;
        else if (time > 90) timeMod = -5;
        
        // Confidence weight
        double confMod = (confidence - 50) * 0.1;
        
        double finalScore = Math.max(0, Math.min(100, baseScore + timeMod + confMod));
        
        Double running = interview.getRunningScore();
        double newRunning = (running == null) ? finalScore : running * 0.6 + finalScore * 0.4;
        interview.setRunningScore(newRunning);

        int baseIdx = levelIndex(interview.getDifficulty());
        int currentIdx = interview.getAdaptedDifficulty() != null ? levelIndex(interview.getAdaptedDifficulty()) : baseIdx;
        
        int target = currentIdx;
        if (newRunning >= 80) target++;
        else if (newRunning <= 45) target--;

        // Constraints:
        // Fresher (BEGINNER): Easy -> Medium (1 -> 2)
        // Experienced (INTERMEDIATE): Medium -> Hard (2 -> 3)
        if (baseIdx == 1) target = Math.max(1, Math.min(2, target));
        else if (baseIdx == 2) target = Math.max(2, Math.min(3, target));
        else if (baseIdx == 3) target = 3;

        interview.setAdaptedDifficulty(displayForIndex(target));
    }

    /** Build resume context for follow-up generation (resume-aware probing). */
    private String buildFollowUpResumeContext(Interview interview) {
        if (interview.getResumeId() != null) {
            return resumeRepository.findById(interview.getResumeId())
                    .map(r -> resumeService.prepareResumeContext(r.getResumeText(), r.getStructuredSkills()))
                    .orElse(null);
        }
        return interview.getResumeText() != null ? interview.getResumeText() : null;
    }

    private int levelIndex(String difficulty) {
        if ("ADVANCED".equalsIgnoreCase(difficulty)) return 3;
        if ("BEGINNER".equalsIgnoreCase(difficulty)) return 1;
        return 2; // INTERMEDIATE (default)
    }

    private String displayForIndex(int idx) {
        return switch (idx) {
            case 1 -> "BEGINNER";
            case 3 -> "ADVANCED";
            default -> "INTERMEDIATE";
        };
    }

    /** Map a display difficulty ("BEGINNER"/"INTERMEDIATE"/"ADVANCED") to Easy/Medium/Hard. */
    private String levelWordFor(String difficulty) {
        return difficulty; // Now fully unified across prompt mapping and backend engine
    }

    /**
     * Run the candidate's code through Judge0 against the question's test cases and persist
     * the execution result + metrics. Returns the aggregated {@link Judge0Result}, or
     * {@code null} when Judge0 is unavailable (caller falls back to AI code evaluation).
     */
    private Judge0Result runJudge0(Interview interview, Question question, String code, String language) {
        Judge0Result result = null;
        try {
            List<TestCase> testCases = question != null ? question.getTestCases() : null;
            result = judge0Service.execute(code, language, testCases);

            // Save to new CodeSubmission entity (Phase 4 spec)
            com.mockinterview.entity.CodeSubmission submission = com.mockinterview.entity.CodeSubmission.builder()
                    .interview(interview)
                    .code(code)
                    .language(language)
                    .build();

            if (result != null) {
                submission.setExecutionTime(result.getExecutionTime());
                submission.setMemoryUsage(result.getMemoryUsage());
                submission.setStatus(result.getStatusDescription());
            }
            submission = codeSubmissionRepository.save(submission);

            if (result != null) {
                submission.setPassedTests(result.getPassedTests());
                submission.setTotalTests(result.getTotalTests());
                submission.setCompileOutput(result.getCompileOutput());
                submission.setStdout(result.getStdout());
                submission.setStderr(result.getStderr());
                submission.setPassed(result.getPassedTests() == result.getTotalTests());
                codeSubmissionRepository.save(submission);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to record Judge0 submission: " + e.getMessage());
        }
        return result;
    }

    private QuestionDTO toQuestionDTO(Question q) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(q.getId());
        dto.setText(q.getQuestionText());
        dto.setType(q.getType());
        dto.setIsCodeQuestion(q.getIsCodeQuestion());
        dto.setCodeSnippet(q.getCodeSnippet());
        dto.setCodeLanguage(q.getCodeLanguage());
        dto.setCodeType(q.getCodeType());
        // Map new coding problem detail fields
        dto.setTitle(q.getTitle());
        dto.setProblemDescription(q.getProblemDescription());
        dto.setExampleInput(q.getExampleInput());
        dto.setExampleOutput(q.getExampleOutput());
        dto.setConstraints(q.getConstraints());
        // starterCode takes priority over codeSnippet for the editor pre-fill
        if (q.getStarterCode() != null && !q.getStarterCode().isBlank()) {
            dto.setStarterCode(q.getStarterCode());
            if (dto.getCodeSnippet() == null) dto.setCodeSnippet(q.getStarterCode());
        }
        dto.setTags(q.getTags());
        dto.setTimeComplexity(q.getTimeComplexity());
        return dto;
    }

    private List<QuestionDTO> toQuestionDTOs(List<Question> questions) {
        return questions.stream().map(this::toQuestionDTO).collect(Collectors.toList());
    }
    private String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 7 — Run Code (sample tests only, does NOT advance the interview)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Executes code against visible (non-hidden) test cases ONLY.
     * No scoring is applied and the interview position is not advanced.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> runCodeSample(Long userId, Long interviewId, AnswerRequest request) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));

        List<Question> questions = getOrderedQuestions(interview.getId());
        if (questions.isEmpty()) {
            throw new IllegalStateException("No questions found for this interview");
        }

        int currentSeq = interview.getCurrentQuestion();
        if (currentSeq < 1 || currentSeq > questions.size()) {
            throw new IllegalStateException("No active question.");
        }
        Question question = questions.get(currentSeq - 1);

        // Execute only VISIBLE (non-hidden) test cases
        List<TestCase> visibleOnly = question.getTestCases() == null ? List.of()
                : question.getTestCases().stream()
                          .filter(tc -> !tc.isHidden())
                          .collect(Collectors.toList());

        Judge0Result result = judge0Service.execute(request.getCode(), request.getLanguage(), visibleOnly);

        Map<String, Object> response = new LinkedHashMap<>();
        if (result == null) {
            response.put("error", "Code execution service is unavailable. Please submit your final answer.");
            return response;
        }
        response.put("passed", result.isPassed());
        response.put("passedTests", result.getPassedTests());
        response.put("totalTests", result.getTotalTests());
        response.put("stdout", result.getStdout());
        response.put("stderr", result.getStderr());
        response.put("executionTime", result.getExecutionTime());
        response.put("memoryUsage", result.getMemoryUsage());
        response.put("statusDescription", result.getStatusDescription());
        response.put("compileOutput", result.getCompileOutput());
        response.put("note", "Sample test results only. Hidden test cases run on final submission.");
        return response;
    }
}



