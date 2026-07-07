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

import java.util.List;
import java.util.Map;

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

    @Transactional
    public InterviewResponse startInterview(Long userId, InterviewRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Interview interview = Interview.builder()
                .user(user)
                .interviewType(request.getRole())
                .resumeText(request.getResumeText())
                .totalQuestions(request.getTotalQuestions())
                .currentQuestion(1)
                .status("in_progress")
                .build();

        interview = interviewRepository.save(interview);

        // Intro question
        Question introQ = Question.builder()
                .interview(interview)
                .questionText("Tell me about yourself \u2014 your background, what you're currently working on, and what excites you about this role.")
                .type("behavioral")
                .isCodeQuestion(false)
                .generatedByAI(false)
                .build();
        questionRepository.save(introQ);

        // Generate AI questions
        String prompt = "Generate " + (request.getTotalQuestions() - 1) + " interview questions for a " + request.getRole() + " role. Resume context: " + request.getResumeText() + ". Format as JSON array of objects with 'text', 'type', and 'isCodeQuestion' boolean.";
        String geminiResponse = geminiService.askGemini(prompt);
        
        try {
            List<Map<String, Object>> aiQuestions = objectMapper.readValue(geminiResponse, List.class);
            for (Map<String, Object> qMap : aiQuestions) {
                Question q = Question.builder()
                        .interview(interview)
                        .questionText((String) qMap.get("text"))
                        .type((String) qMap.get("type"))
                        .isCodeQuestion(qMap.get("isCodeQuestion") instanceof Boolean ? (Boolean) qMap.get("isCodeQuestion") : false)
                        .generatedByAI(true)
                        .build();
                questionRepository.save(q);
            }
        } catch (Exception e) {
            // Fallback
            Question fallbackQ = Question.builder()
                    .interview(interview)
                    .questionText("Describe a challenging project you worked on.")
                    .type("behavioral")
                    .isCodeQuestion(false)
                    .generatedByAI(false)
                    .build();
            questionRepository.save(fallbackQ);
        }

        List<Question> savedQuestions = questionRepository.findByInterviewId(interview.getId());
        interview.setQuestions(savedQuestions);

        InterviewResponse response = interviewMapper.toDTO(interview);
        response.setGreeting("Hi! Let's begin the interview.");
        if (!savedQuestions.isEmpty()) {
            QuestionDTO qDto = new QuestionDTO();
            qDto.setId(savedQuestions.get(0).getId());
            qDto.setText(savedQuestions.get(0).getQuestionText());
            qDto.setType(savedQuestions.get(0).getType());
            qDto.setIsCodeQuestion(savedQuestions.get(0).getIsCodeQuestion());
            response.setQuestion(qDto);
        }

        return response;
    }

    @Transactional
    public AnswerResponse submitAnswer(Long userId, Long interviewId, AnswerRequest request) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));

        if ("completed".equals(interview.getStatus())) {
            throw new IllegalArgumentException("Interview is already completed");
        }

        List<Question> questions = questionRepository.findByInterviewId(interview.getId());
        int currentIndex = interview.getCurrentQuestion() - 1;
        Question currentQ = questions.get(currentIndex);

        Answer answer = Answer.builder()
                .question(currentQ)
                .answerText(request.getAnswerText())
                .codeLanguage(request.getLanguage())
                .build();
        
        if (request.getCode() != null) {
            answer.setAnswerText(request.getCode()); // simplify storage
            String evalPrompt = "Evaluate this " + request.getLanguage() + " code for question: " + currentQ.getQuestionText() + ". Code: " + request.getCode() + ". Return JSON { \"score\": number, \"feedback\": string }";
            String eval = geminiService.askGemini(evalPrompt);
            try {
                Map<String, Object> evalMap = objectMapper.readValue(eval, Map.class);
                answer.setEvaluationScore(evalMap.get("score") != null ? Double.valueOf(evalMap.get("score").toString()) : 0.0);
                answer.setFeedback((String) evalMap.get("feedback"));
            } catch (JsonProcessingException e) {
                answer.setEvaluationScore(0.0);
            }
        }
        answerRepository.save(answer);

        interview.setCurrentQuestion(interview.getCurrentQuestion() + 1);
        interviewRepository.save(interview);

        AnswerResponse response = new AnswerResponse();
        
        if (interview.getCurrentQuestion() > interview.getTotalQuestions() || interview.getCurrentQuestion() > questions.size()) {
            response.setComplete(true);
            response.setMessage("Thank you for completing the interview!");
            endInterview(userId, interviewId);
        } else {
            response.setComplete(false);
            response.setCurrentQuestion(interview.getCurrentQuestion());
            response.setTotalQuestions(interview.getTotalQuestions());
            
            Question nextQ = questions.get(interview.getCurrentQuestion() - 1);
            QuestionDTO nextQDto = new QuestionDTO();
            nextQDto.setId(nextQ.getId());
            nextQDto.setText(nextQ.getQuestionText());
            nextQDto.setType(nextQ.getType());
            nextQDto.setIsCodeQuestion(nextQ.getIsCodeQuestion());
            response.setQuestion(nextQDto);
            
            response.setResponse("Great. Let's move to the next question.");
        }
        
        return response;
    }

    @Transactional
    public FeedbackResponse endInterview(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));

        interview.setStatus("completed");
        
        String feedbackPrompt = "Provide overall feedback and a score out of 100 for a " + interview.getInterviewType() + " interview. Return JSON { \"overallScore\": number, \"strengths\": string, \"improvements\": string }";
        String geminiResponse = geminiService.askGemini(feedbackPrompt);
        
        Double score = 0.0;
        try {
            Map<String, Object> fMap = objectMapper.readValue(geminiResponse, Map.class);
            score = fMap.get("overallScore") != null ? Double.valueOf(fMap.get("overallScore").toString()) : 0.0;
            interview.setFeedback(geminiResponse);
            interview.setScore(score);
            
            InterviewHistory history = InterviewHistory.builder()
                    .user(interview.getUser())
                    .interview(interview)
                    .totalScore(score)
                    .strengths((String) fMap.get("strengths"))
                    .improvements((String) fMap.get("improvements"))
                    .build();
            historyRepository.save(history);
        } catch (Exception ignored) {}

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

    @Transactional(readOnly = true)
    public InterviewResponse getInterview(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
        return interviewMapper.toDTO(interview);
    }

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
}
