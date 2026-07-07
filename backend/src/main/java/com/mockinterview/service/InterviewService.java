package com.mockinterview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.dto.*;
import com.mockinterview.entity.*;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.mapper.InterviewMapper;
import com.mockinterview.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final InterviewHistoryRepository historyRepository;
    private final GeminiService geminiService;
    private final UserRepository userRepository;
    private final InterviewMapper interviewMapper;
    private final ObjectMapper objectMapper;
    private final SpeechToTextService speechToTextService;
    private final TextToSpeechService textToSpeechService;

    public InterviewService(InterviewRepository interviewRepository, QuestionRepository questionRepository,
                            AnswerRepository answerRepository, InterviewHistoryRepository historyRepository,
                            GeminiService geminiService, UserRepository userRepository,
                            InterviewMapper interviewMapper, SpeechToTextService speechToTextService,
                            TextToSpeechService textToSpeechService) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.historyRepository = historyRepository;
        this.geminiService = geminiService;
        this.userRepository = userRepository;
        this.interviewMapper = interviewMapper;
        this.speechToTextService = speechToTextService;
        this.textToSpeechService = textToSpeechService;
        this.objectMapper = new ObjectMapper();
    }

    // ─────────────────────────────────────────────────────────
    // START INTERVIEW
    // ─────────────────────────────────────────────────────────

    @Transactional
    public InterviewResponse startInterview(Long userId, InterviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Clamp totalQuestions between 5 and 20
        int totalQ = request.getTotalQuestions() != null
                ? Math.max(5, Math.min(20, request.getTotalQuestions()))
                : 10;

        Interview interview = Interview.builder()
                .user(user)
                .interviewType(request.getRole())
                .resumeText(request.getResumeText())
                .totalQuestions(totalQ)
                .currentQuestion(1)
                .status("in_progress")
                .build();

        interview = interviewRepository.save(interview);

        // ── Question 1: fixed intro question ──────────────────
        Question introQ = Question.builder()
                .interview(interview)
                .questionText("Tell me about yourself — your background, what you're currently working on, and what excites you about this role.")
                .type("behavioral")
                .isCodeQuestion(false)
                .generatedByAI(false)
                .build();
        questionRepository.save(introQ);

        // ── Questions 2–N: AI-generated ───────────────────────
        int aiQuestionsNeeded = totalQ - 1;
        generateAndSaveAIQuestions(interview, request.getRole(), request.getResumeText(), aiQuestionsNeeded);

        // ── Build response ────────────────────────────────────
        List<Question> savedQuestions = questionRepository.findByInterviewId(interview.getId());
        InterviewResponse response = interviewMapper.toDTO(interview);
        response.setGreeting("Hi! I'm Natalie, your AI interviewer. Let's begin!");

        List<QuestionDTO> questionDTOs = toQuestionDTOs(savedQuestions);
        response.setQuestions(questionDTOs);
        response.setMessages(List.of()); // initialize to empty list, not null

        if (!questionDTOs.isEmpty()) {
            response.setQuestion(questionDTOs.get(0));
        }

        return response;
    }

    /**
     * Calls Gemini to generate `count` questions and saves them to DB.
     * Falls back gracefully if AI fails.
     */
    private void generateAndSaveAIQuestions(Interview interview, String role, String resumeText, int count) {
        // Explicit prompt: return ONLY a raw JSON array — no markdown, no preamble
        String prompt = String.format(
            "You are an expert technical interviewer. Generate exactly %d interview questions for a %s role candidate.\n\n" +
            "Resume context: %s\n\n" +
            "RULES:\n" +
            "- Return ONLY a raw JSON array. No markdown. No explanation. No ```json fences.\n" +
            "- Each element must have exactly these fields: \"text\" (string), \"type\" (string: behavioral|technical|problem-solving|situational), \"isCodeQuestion\" (boolean)\n" +
            "- Mix question types: ~40%% behavioral, ~40%% technical, ~20%% problem-solving\n" +
            "- For technical roles, include 1-2 questions with \"isCodeQuestion\": true\n" +
            "- Make questions specific to the candidate's resume and the role\n\n" +
            "Example output format:\n" +
            "[{\"text\": \"Explain the difference between REST and GraphQL\", \"type\": \"technical\", \"isCodeQuestion\": false}]\n\n" +
            "Generate %d questions now:",
            count, role, resumeText != null ? resumeText.substring(0, Math.min(resumeText.length(), 2000)) : "Not provided", count
        );

        String geminiResponse = geminiService.askGemini(prompt);

        boolean savedFromAI = false;
        try {
            List<Map<String, Object>> aiQuestions = objectMapper.readValue(geminiResponse, List.class);
            if (aiQuestions != null && !aiQuestions.isEmpty()) {
                int saved = 0;
                for (Map<String, Object> qMap : aiQuestions) {
                    String text = (String) qMap.get("text");
                    if (text == null || text.isBlank()) continue;
                    String type = qMap.get("type") instanceof String t ? t : "technical";
                    boolean isCode = qMap.get("isCodeQuestion") instanceof Boolean b ? b : false;

                    Question q = Question.builder()
                            .interview(interview)
                            .questionText(text.trim())
                            .type(type)
                            .isCodeQuestion(isCode)
                            .generatedByAI(true)
                            .build();
                    questionRepository.save(q);
                    saved++;
                    if (saved >= count) break; // don't exceed requested count
                }
                savedFromAI = saved > 0;
                System.out.println("✅ AI generated " + saved + " questions for interview " + interview.getId());
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to parse AI questions for interview " + interview.getId() + ": " + e.getMessage());
            System.err.println("   Raw Gemini response (first 500 chars): " + geminiResponse.substring(0, Math.min(geminiResponse.length(), 500)));
        }

        // Fallback: if AI failed, generate generic fallback questions
        if (!savedFromAI) {
            System.err.println("🔁 Using fallback questions for interview " + interview.getId());
            saveFallbackQuestions(interview, role, count);
        }
    }

    private void saveFallbackQuestions(Interview interview, String role, int count) {
        List<String[]> fallbacks = new ArrayList<>(List.of(
            new String[]{"Describe a challenging technical problem you solved recently.", "behavioral", "false"},
            new String[]{"How do you approach debugging a complex issue in production?", "problem-solving", "false"},
            new String[]{"What are the key principles of clean code and how do you apply them?", "technical", "false"},
            new String[]{"How do you handle disagreements with teammates on technical decisions?", "behavioral", "false"},
            new String[]{"Walk me through your experience with version control and CI/CD pipelines.", "technical", "false"},
            new String[]{"Describe your approach to performance optimization in an application.", "technical", "false"},
            new String[]{"How do you stay up to date with new technologies and industry trends?", "behavioral", "false"},
            new String[]{"Explain how you would design a scalable system for a high-traffic application.", "technical", "false"},
            new String[]{"Give an example of when you had to meet a tight deadline. How did you manage it?", "situational", "false"},
            new String[]{"What is your process for writing and maintaining unit tests?", "technical", "false"},
            new String[]{"Describe a time you had to learn a new technology quickly.", "behavioral", "false"},
            new String[]{"How do you prioritize tasks when working on multiple projects simultaneously?", "problem-solving", "false"},
            new String[]{"What are the most important things to consider when reviewing a pull request?", "technical", "false"},
            new String[]{"Describe your experience with databases and data modeling.", "technical", "false"},
            new String[]{"What does good documentation look like to you, and how do you write it?", "behavioral", "false"}
        ));

        for (int i = 0; i < Math.min(count, fallbacks.size()); i++) {
            String[] fb = fallbacks.get(i);
            Question q = Question.builder()
                    .interview(interview)
                    .questionText(fb[0])
                    .type(fb[1])
                    .isCodeQuestion(Boolean.parseBoolean(fb[2]))
                    .generatedByAI(false)
                    .build();
            questionRepository.save(q);
        }
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

        List<Question> questions = questionRepository.findByInterviewId(interview.getId());
        if (questions.isEmpty()) {
            throw new IllegalStateException("No questions found for this interview");
        }

        int currentIndex = interview.getCurrentQuestion() - 1;
        if (currentIndex < 0 || currentIndex >= questions.size()) {
            throw new IllegalStateException("Invalid question index: " + currentIndex);
        }
        Question currentQ = questions.get(currentIndex);

        // ── Save the answer ───────────────────────────────────
        String answerText = request.getCode() != null && !request.getCode().isBlank()
                ? request.getCode()
                : request.getAnswerText();

        Answer answer = Answer.builder()
                .question(currentQ)
                .answerText(answerText)
                .codeLanguage(request.getLanguage())
                .build();

        // ── Evaluate the answer with AI ───────────────────────
        evaluateAnswer(answer, currentQ, request);

        answerRepository.save(answer);

        // ── Advance the question counter ──────────────────────
        int nextQuestion = interview.getCurrentQuestion() + 1;
        interview.setCurrentQuestion(nextQuestion);
        interviewRepository.save(interview);

        // ── Build response ────────────────────────────────────
        AnswerResponse response = new AnswerResponse();

        boolean isLastQuestion = nextQuestion > interview.getTotalQuestions()
                || nextQuestion > questions.size();

        if (isLastQuestion) {
            response.setComplete(true);
            response.setMessage("Thank you for completing the interview! Generating your detailed feedback report...");
            // Auto-end the interview
            endInterview(userId, interviewId);
        } else {
            response.setComplete(false);
            response.setCurrentQuestion(nextQuestion);
            response.setTotalQuestions(interview.getTotalQuestions());

            Question nextQ = questions.get(nextQuestion - 1);
            QuestionDTO nextQDto = toQuestionDTO(nextQ);
            response.setQuestion(nextQDto);
            response.setResponse("Good answer! Let's move to the next question.");
        }

        return response;
    }

    /**
     * Evaluates an answer using Gemini AI and populates the answer's score + feedback.
     */
    private void evaluateAnswer(Answer answer, Question question, AnswerRequest request) {
        try {
            String evalPrompt;
            if (request.getCode() != null && !request.getCode().isBlank()) {
                evalPrompt = String.format(
                    "Evaluate this %s code submission for the interview question below.\n\n" +
                    "Question: %s\n\n" +
                    "Code submitted:\n%s\n\n" +
                    "Return ONLY raw JSON (no markdown, no fences):\n" +
                    "{\"score\": <0-100>, \"feedback\": \"<concise evaluation>\", \"isCorrect\": <true|false>}",
                    request.getLanguage() != null ? request.getLanguage() : "unknown",
                    question.getQuestionText(),
                    request.getCode()
                );
            } else {
                evalPrompt = String.format(
                    "Evaluate this interview answer.\n\n" +
                    "Question: %s\n\n" +
                    "Candidate's answer: %s\n\n" +
                    "Return ONLY raw JSON (no markdown, no fences):\n" +
                    "{\"score\": <0-100>, \"feedback\": \"<1-2 sentence evaluation>\"}",
                    question.getQuestionText(),
                    answer.getAnswerText() != null ? answer.getAnswerText() : "No answer provided"
                );
            }

            String eval = geminiService.askGemini(evalPrompt);
            Map<String, Object> evalMap = objectMapper.readValue(eval, Map.class);

            if (evalMap.get("score") != null) {
                answer.setEvaluationScore(Double.valueOf(evalMap.get("score").toString()));
            }
            if (evalMap.get("feedback") instanceof String fb) {
                answer.setFeedback(fb);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to evaluate answer: " + e.getMessage());
            answer.setEvaluationScore(70.0); // neutral fallback score
            answer.setFeedback("Answer recorded.");
        }
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

        // ── Build evaluation prompt with actual answers ───────
        String feedbackPrompt = String.format("""
                You are an expert technical interview evaluator.
                
                Interview role: %s
                
                Here are all the interview questions and the candidate's answers:
                %s
                
                Based on the candidate's actual answers above, provide a detailed evaluation.
                Return ONLY valid JSON (no markdown, no extra text) in this exact format:
                {
                  "overallScore": <number 0-100>,
                  "categoryScores": {
                    "communicationSkills": { "score": <0-100>, "comment": "<specific comment based on answers>" },
                    "technicalKnowledge":  { "score": <0-100>, "comment": "<specific comment based on answers>" },
                    "problemSolving":      { "score": <0-100>, "comment": "<specific comment based on answers>" },
                    "codeQuality":         { "score": <0-100>, "comment": "<specific comment based on answers>" },
                    "confidence":          { "score": <0-100>, "comment": "<specific comment based on answers>" }
                  },
                  "strengths": ["<strength1>", "<strength2>", "<strength3>"],
                  "areasOfImprovement": ["<area1>", "<area2>"],
                  "finalAssessment": "<3-4 sentence overall assessment based on the actual answers given>"
                }
                """, interview.getInterviewType(), qaContext);

        String geminiResponse = geminiService.askGemini(feedbackPrompt);

        Double score = 0.0;
        try {
            Map<String, Object> fMap = objectMapper.readValue(geminiResponse, Map.class);
            score = fMap.get("overallScore") != null
                    ? Double.valueOf(fMap.get("overallScore").toString())
                    : calculateFallbackScore(questions);
            interview.setFeedback(geminiResponse);
            interview.setScore(score);

            // Extract plain-text strengths/improvements for InterviewHistory
            String strengthsStr = extractListAsString(fMap, "strengths");
            String improvementsStr = extractListAsString(fMap, "areasOfImprovement");

            final double finalScore = score;
            final String finalStrengths = strengthsStr;
            final String finalImprovements = improvementsStr;

            // Create or update history entry
            historyRepository.findByInterviewId(interview.getId()).ifPresentOrElse(
                existing -> {
                    existing.setTotalScore(finalScore);
                    existing.setStrengths(finalStrengths);
                    existing.setImprovements(finalImprovements);
                    historyRepository.save(existing);
                },
                () -> {
                    InterviewHistory history = InterviewHistory.builder()
                            .user(interview.getUser())
                            .interview(interview)
                            .totalScore(finalScore)
                            .strengths(finalStrengths)
                            .improvements(finalImprovements)
                            .build();
                    historyRepository.save(history);
                }
            );

        } catch (Exception e) {
            System.err.println("⚠️ Failed to parse feedback from Gemini: " + e.getMessage());
            // Build a fallback feedback JSON from per-answer scores
            score = calculateFallbackScore(questions);
            String fallbackFeedback = buildFallbackFeedbackJson(score, questions);
            interview.setFeedback(fallbackFeedback);
            interview.setScore(score);

            final double fs = score;
            historyRepository.findByInterviewId(interview.getId()).ifPresentOrElse(
                existing -> { existing.setTotalScore(fs); historyRepository.save(existing); },
                () -> historyRepository.save(InterviewHistory.builder()
                        .user(interview.getUser()).interview(interview).totalScore(fs).build())
            );
        }

        interviewRepository.save(interview);

        FeedbackResponse response = new FeedbackResponse();
        response.setInterviewId(interview.getId().toString());
        response.setOverallScore(score);
        try {
            response.setFeedback(objectMapper.readValue(interview.getFeedback(), Map.class));
        } catch (Exception e) {
            response.setFeedback(interview.getFeedback());
        }
        return response;
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

    public String transcribeAudio(org.springframework.web.multipart.MultipartFile audio) {
        return speechToTextService.transcribeAudio(audio);
    }

    @Transactional
    public AnswerResponse submitVoiceAnswer(Long userId, Long interviewId, org.springframework.web.multipart.MultipartFile audio) {
        String transcribedText = transcribeAudio(audio);
        AnswerRequest request = new AnswerRequest();
        request.setAnswerText(transcribedText);
        return submitAnswer(userId, interviewId, request);
    }

    public org.springframework.core.io.Resource speakText(String text) {
        return textToSpeechService.synthesizeSpeech(text);
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
            } else {
                sb.append("A: [No answer provided]\n");
            }
        }
        return sb.toString();
    }

    private double calculateFallbackScore(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return 70.0;
        double total = 0;
        int count = 0;
        for (Question q : questions) {
            if (q.getAnswers() != null) {
                for (Answer a : q.getAnswers()) {
                    if (a.getEvaluationScore() != null) {
                        total += a.getEvaluationScore();
                        count++;
                    }
                }
            }
        }
        return count > 0 ? Math.round(total / count) : 70.0;
    }

    private String buildFallbackFeedbackJson(double score, List<Question> questions) {
        try {
            Map<String, Object> feedback = Map.of(
                "overallScore", score,
                "categoryScores", Map.of(
                    "communicationSkills", Map.of("score", score, "comment", "Based on submitted answers."),
                    "technicalKnowledge", Map.of("score", score, "comment", "Based on submitted answers."),
                    "problemSolving", Map.of("score", score, "comment", "Based on submitted answers."),
                    "codeQuality", Map.of("score", score, "comment", "Based on submitted answers."),
                    "confidence", Map.of("score", score, "comment", "Based on submitted answers.")
                ),
                "strengths", List.of("Completed the interview", "Engaged with all questions"),
                "areasOfImprovement", List.of("Continue practicing interview techniques"),
                "finalAssessment", "The candidate completed the interview session. Overall score: " + score + "/100."
            );
            return objectMapper.writeValueAsString(feedback);
        } catch (JsonProcessingException e) {
            return "{\"overallScore\": " + score + ", \"strengths\": [], \"areasOfImprovement\": [], \"finalAssessment\": \"Interview completed.\"}";
        }
    }

    private String extractListAsString(Map<String, Object> map, String key) {
        try {
            Object val = map.get(key);
            if (val instanceof List<?> list) return list.toString();
        } catch (Exception ignored) {}
        return "";
    }

    private FeedbackResponse buildExistingFeedbackResponse(Interview interview) {
        FeedbackResponse existing = new FeedbackResponse();
        existing.setInterviewId(interview.getId().toString());
        existing.setOverallScore(interview.getScore());
        try {
            existing.setFeedback(objectMapper.readValue(interview.getFeedback(), Map.class));
        } catch (Exception e) {
            existing.setFeedback(interview.getFeedback());
        }
        return existing;
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
        return dto;
    }

    private List<QuestionDTO> toQuestionDTOs(List<Question> questions) {
        return questions.stream().map(this::toQuestionDTO).collect(Collectors.toList());
    }
}
