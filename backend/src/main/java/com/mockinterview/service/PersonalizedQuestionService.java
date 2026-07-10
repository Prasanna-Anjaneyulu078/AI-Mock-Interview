package com.mockinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.entity.TestCase;
import com.mockinterview.repository.QuestionRepository;
import com.mockinterview.service.ai.AIProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Service
public class PersonalizedQuestionService {

    private final AIProvider aiProvider;
    private final QuestionRepository questionRepository;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;

    private static final double DEDUP_THRESHOLD = 0.7;
    private static final int MAX_ATTEMPTS = 2;

    private static final Map<String, String> LEVEL_DIFFICULTY = Map.of(
            "STARTER", "Easy",
            "BEGINNER", "Easy",
            "STANDARD", "Medium",
            "INTERMEDIATE", "Medium",
            "ADVANCED", "Hard"
    );

    private static final Map<String, String> LEVEL_GUIDANCE = Map.of(
            "STARTER", "Focus ONLY on fundamentals: definitions, core concepts, basic coding constructs, and simple entry-level interview questions.",
            "BEGINNER", "Focus ONLY on fundamentals: definitions, core concepts, basic coding constructs, and simple entry-level interview questions.",
            "STANDARD", "Focus on real-world development scenarios, debugging questions, applied concepts, and practical implementation questions drawn from the candidate's own projects.",
            "INTERMEDIATE", "Focus on real-world development scenarios, debugging questions, applied concepts, and practical implementation questions drawn from the candidate's own projects.",
            "ADVANCED", "Focus on system design, software architecture, scalability, performance optimization, and deep trade-off discussions."
    );

    public PersonalizedQuestionService(AIProvider aiProvider, QuestionRepository questionRepository,
                                       ResumeService resumeService) {
        this.aiProvider = aiProvider;
        this.questionRepository = questionRepository;
        this.resumeService = resumeService;
        this.objectMapper = new ObjectMapper();
    }

    public void generateAndSaveAIQuestions(Interview interview, String role, String resumeText,
                                           String structuredProfile, int hrCount, int techCount, 
                                           int projCount, int codeCount, int interestCount, String level,
                                           Long userId, Long resumeId) {
        if (level == null) level = "STANDARD";
        String lvl = level.toUpperCase();
        String levelDifficulty = LEVEL_DIFFICULTY.getOrDefault(lvl, "Medium");
        String guidance = LEVEL_GUIDANCE.getOrDefault(lvl, LEVEL_GUIDANCE.get("STANDARD"));
        
        if (interview.getInterviewMode() != null && interview.getInterviewMode().equals("INTEREST_BASED") 
            && interview.getSelectedInterests() != null) {
            guidance += "\nCRITICAL CONSTRAINT: ONLY ask questions directly related to the following interests: " + interview.getSelectedInterests();
        }
        
        int count = hrCount + techCount + projCount + codeCount + interestCount;
        if (count == 0) return;

        Set<String> seen = new HashSet<>();
        List<Question> past = new ArrayList<>();
        try {
            past.addAll(questionRepository.findByInterviewUserId(userId));
            if (resumeId != null) {
                past.addAll(questionRepository.findByInterviewResumeId(resumeId));
            }
        } catch (Exception ignored) {}
        
        for (Question q : past) {
            if (q.getQuestionText() != null) {
                seen.add(TextSimilarity.normalize(q.getQuestionText()));
            }
        }

        String resumeContext = resumeService.prepareResumeContext(resumeText, structuredProfile);
        String avoidList = "";
        // Phase 5: ensure at least codeCount coding questions are generated
        int minCode = codeCount;

        List<Question> accepted = new ArrayList<>();
        for (int attempt = 0; attempt < MAX_ATTEMPTS && accepted.size() < count; attempt++) {
            String aiResponse;
            try {
                aiResponse = aiProvider.generateQuestions(role, resumeContext, guidance, levelDifficulty,
                        hrCount, techCount, projCount, codeCount, interestCount, interview.getSelectedInterests(), count, avoidList);
            } catch (Exception e) {
                System.err.println("⚠️ AI failed to generate questions: " + e.getMessage());
                break;
            }
            
            aiResponse = extractJson(aiResponse);
            List<Map<String, Object>> parsed = parseArray(aiResponse);
            if (parsed == null) continue;
            
            for (Map<String, Object> qMap : parsed) {
                String text = firstNonBlank(qMap, "question", "text");
                if (text == null) continue;
                if (TextSimilarity.isDuplicate(text, seen, DEDUP_THRESHOLD)) continue;
                
                Question q = toQuestion(interview, qMap, text, levelDifficulty);
                if (q == null) continue;
                
                accepted.add(q);
                seen.add(TextSimilarity.normalize(text));
                if (accepted.size() >= count) break;
            }
            avoidList = accepted.stream().map(Question::getQuestionText).collect(Collectors.joining("\n- "));
        }

        try {
            // Phase 5: guarantee minimum coding questions
            long codeQsInAccepted = accepted.stream()
                    .filter(q -> Boolean.TRUE.equals(q.getIsCodeQuestion())).count();
            if (codeQsInAccepted < minCode) {
                String genLang = interview.getCodingLanguage() != null ? interview.getCodingLanguage() : "java";
                addFallbackCodingQuestions(interview, accepted, (int)(minCode - codeQsInAccepted), role, levelDifficulty, seen, genLang);
            }

            for (Question q : accepted) questionRepository.save(q);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save AI questions for interview " + interview.getId() + ": " + e.getMessage());
        }

        if (accepted.size() < count) {
            forceSaveFallbackQuestions(interview, count - accepted.size(), seen);
        }
    }

    public String generateIntroQuestion(String role, String structuredProfile) {
        try {
            String response = aiProvider.generateIntroQuestion(role, structuredProfile);
            if (response != null) {
                String cleaned = response.replaceAll("^[\"'`]+|[\"'`]+$", "").trim();
                if (!cleaned.isBlank() && cleaned.length() < 300) return cleaned;
            }
        } catch (Exception ignored) {}
        return fallbackIntro(role, structuredProfile);
    }

    private String extractJson(String text) {
        if (text == null) return "[]";
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

    private List<Map<String, Object>> parseArray(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            Object parsed = objectMapper.readValue(response, Object.class);
            if (parsed instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) parsed;
                return list;
            }
            return null;
        } catch (Exception e) {
            System.err.println("⚠️ Failed to parse AI question JSON: " + e.getMessage());
            return null;
        }
    }

    private Question toQuestion(Interview interview, Map<String, Object> qMap, String text, String levelDifficulty) {
        String category = firstNonBlank(qMap, "category", "type");
        String type = normalizeCategory(category);
        boolean isCode = qMap.get("isCodeQuestion") instanceof Boolean b && b;
        String idealAnswer = qMap.get("idealAnswer") instanceof String s ? s : "";
        String explanation = qMap.get("explanation") instanceof String s ? s : "";

        // Parse coding problem detail fields (Issue #2)
        String title = qMap.get("title") instanceof String s ? s : null;
        String problemDescription = qMap.get("problemDescription") instanceof String s ? s : null;
        String exampleInput = qMap.get("exampleInput") instanceof String s ? s : null;
        String exampleOutput = qMap.get("exampleOutput") instanceof String s ? s : null;
        String constraints = qMap.get("constraints") instanceof String s ? s : null;
        String starterCode = firstNonBlank(qMap, "starterCode", "codeSnippet");
        String tags = qMap.get("tags") instanceof String s ? s : null;
        String timeComplexity = qMap.get("timeComplexity") instanceof String s ? s : null;
        String codeType = qMap.get("codeType") instanceof String s ? s : (isCode ? "write" : null);
        String codeLanguage = firstNonBlank(qMap, "codeLanguage", "language");
        if (codeLanguage == null && isCode && interview.getCodingLanguage() != null) {
            codeLanguage = interview.getCodingLanguage();
        }

        Question q = Question.builder()
                .interview(interview)
                .questionText(text.trim())
                .type(type)
                .isCodeQuestion(isCode)
                .expectedAnswer(idealAnswer)
                .explanation(explanation)
                .difficulty(levelDifficulty)
                .generatedByAI(true)
                .title(title)
                .problemDescription(problemDescription)
                .exampleInput(exampleInput)
                .exampleOutput(exampleOutput)
                .constraints(constraints)
                .starterCode(starterCode)
                .tags(tags)
                .timeComplexity(timeComplexity)
                .codeType(codeType)
                .codeLanguage(codeLanguage)
                .build();

        List<TestCase> testCases = parseTestCases(qMap);
        if (testCases != null) {
            for (TestCase tc : testCases) tc.setQuestion(q);
            q.setTestCases(testCases);
        }
        return q;
    }

    private List<TestCase> parseTestCases(Map<String, Object> qMap) {
        Object tcObj = qMap.get("testCases");
        if (!(tcObj instanceof List<?> list) || list.isEmpty()) return null;
        List<TestCase> testCases = new ArrayList<>();
        int idx = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String input = m.get("input") instanceof String s ? s : "";
            String expected = m.get("expectedOutput") instanceof String s ? s : "";
            boolean hidden = m.get("hidden") instanceof Boolean b && b;
            testCases.add(TestCase.builder().input(input).expectedOutput(expected).hidden(hidden).name("case-" + (idx++)).build());
        }
        return testCases.isEmpty() ? null : testCases;
    }

    private String normalizeCategory(String category) {
        if (category == null) return "technical";
        String c = category.toLowerCase();
        if (c.contains("behav") || c.contains("hr")) return "behavioral";
        if (c.contains("proj") || c.contains("experien")) return "project";
        return "technical";
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    public void forceSaveFallbackQuestions(Interview interview, int needed, Set<String> seen) {
        if (seen == null) seen = new HashSet<>();
        
        Map<String, String[]> fallbacks = new LinkedHashMap<>();
        fallbacks.put("How do you approach debugging a complex production issue?", new String[]{"problem-solving", "false"});
        fallbacks.put("Describe a challenging technical problem you solved recently and your solution.", new String[]{"behavioral", "false"});
        fallbacks.put("Explain a design decision you are proud of from a past project.", new String[]{"project", "false"});
        fallbacks.put("How do you ensure code quality and maintainability in your work?", new String[]{"technical", "false"});
        fallbacks.put("Walk me through how you would design a scalable system for a high-traffic application.", new String[]{"technical", "false"});
        fallbacks.put("How do you handle disagreements with teammates on technical decisions?", new String[]{"behavioral", "false"});
        fallbacks.put("Describe your approach to performance optimization in an application.", new String[]{"technical", "false"});
        fallbacks.put("How do you stay current with new technologies and industry trends?", new String[]{"behavioral", "false"});
        fallbacks.put("What is your process for writing and maintaining unit tests?", new String[]{"technical", "false"});
        fallbacks.put("Give an example of when you had to meet a tight deadline. How did you manage it?", new String[]{"situational", "false"});
        
        // Expanded fallbacks for LONG interviews
        fallbacks.put("Tell me about a time you had to learn a new technology quickly.", new String[]{"behavioral", "false"});
        fallbacks.put("How do you prioritize your work when faced with multiple urgent tasks?", new String[]{"situational", "false"});
        fallbacks.put("Describe a time when a project you were working on failed. What did you learn?", new String[]{"behavioral", "false"});
        fallbacks.put("How do you balance technical debt with the need to ship features quickly?", new String[]{"technical", "false"});
        fallbacks.put("What is your approach to code reviews, both giving and receiving feedback?", new String[]{"technical", "false"});
        fallbacks.put("Explain the most complex architecture you have designed or worked on.", new String[]{"project", "false"});
        fallbacks.put("How do you handle shifting requirements from stakeholders?", new String[]{"situational", "false"});
        fallbacks.put("Tell me about a time you mentored a junior team member.", new String[]{"behavioral", "false"});
        fallbacks.put("Describe a situation where you had to compromise on a technical implementation.", new String[]{"behavioral", "false"});
        fallbacks.put("What strategies do you use to ensure application security during development?", new String[]{"technical", "false"});

        int added = 0;
        for (Map.Entry<String, String[]> fb : fallbacks.entrySet()) {
            if (added >= needed) break;
            
            String text = fb.getKey();
            if (TextSimilarity.isDuplicate(text, seen, DEDUP_THRESHOLD)) continue;
            
            Question q = Question.builder()
                    .interview(interview)
                    .questionText(text)
                    .type(fb.getValue()[0])
                    .isCodeQuestion(Boolean.parseBoolean(fb.getValue()[1]))
                    .generatedByAI(false)
                    .build();
            questionRepository.save(q);
            seen.add(TextSimilarity.normalize(text));
            added++;
        }
    }

    /**
     * Phase 5: Adds N coding questions to the accepted list when the AI didn't generate
     * enough. Uses pre-defined challenges covering common algorithm patterns.
     */
    private static final Object[][] CODING_FALLBACK_PROBLEMS = {
        {
            "Write a function to find the two numbers in an array that sum to a target value.",
            "Two Sum",
            "Given an array of integers nums and an integer target, return the indices of the two numbers that add up to target. You may assume exactly one solution exists and you may not use the same element twice.",
            "nums = [2, 7, 11, 15], target = 9",
            "[0, 1]  (because nums[0] + nums[1] = 2 + 7 = 9)",
            "2 <= nums.length <= 10^4\n-10^9 <= nums[i] <= 10^9\nExactly one valid answer exists.",
            "function twoSum(nums, target) {\n  // Your solution here\n}",
            "Array, Hash Map",
            "O(n)"
        },
        {
            "Implement a function that reverses a linked list iteratively.",
            "Reverse Linked List",
            "Given the head of a singly linked list, reverse the list and return the reversed list. The list node has two properties: val (integer) and next (pointer to next node or null).",
            "head = [1, 2, 3, 4, 5]",
            "[5, 4, 3, 2, 1]",
            "The number of nodes is in range [0, 5000]\n-5000 <= Node.val <= 5000",
            "function reverseList(head) {\n  // Your solution here\n}",
            "Linked List, Iteration",
            "O(n)"
        },
        {
            "Write a function to determine if a string is a valid palindrome, ignoring non-alphanumeric characters and case.",
            "Valid Palindrome",
            "A phrase is a palindrome if, after converting all uppercase letters to lowercase and removing all non-alphanumeric characters, it reads the same forward and backward. Given a string s, return true if it is a palindrome, or false otherwise.",
            "s = \"A man, a plan, a canal: Panama\"",
            "true",
            "1 <= s.length <= 2 * 10^5\ns consists only of printable ASCII characters.",
            "function isPalindrome(s) {\n  // Your solution here\n}",
            "String, Two Pointers",
            "O(n)"
        },
        {
            "Given a binary tree, write a function to find its maximum depth.",
            "Maximum Depth of Binary Tree",
            "Given the root of a binary tree, return its maximum depth. Maximum depth is the number of nodes along the longest path from the root node down to the farthest leaf node.",
            "root = [3, 9, 20, null, null, 15, 7]",
            "3",
            "The number of nodes is in [0, 10^4]\n-100 <= Node.val <= 100",
            "function maxDepth(root) {\n  // Your solution here\n}",
            "Tree, DFS, BFS",
            "O(n)"
        },
        {
            "Find the length of the longest substring without repeating characters.",
            "Longest Substring Without Repeating Characters",
            "Given a string s, find the length of the longest substring without repeating characters. A substring is a contiguous sequence of characters within the string.",
            "s = \"abcabcbb\"",
            "3  (the substring 'abc' has length 3)",
            "0 <= s.length <= 5 * 10^4\ns consists of English letters, digits, symbols and spaces.",
            "function lengthOfLongestSubstring(s) {\n  // Your solution here\n}",
            "Hash Map, Sliding Window",
            "O(n)"
        }
    };

    private void addFallbackCodingQuestions(Interview interview, List<Question> accepted,
                                             int needed, String role, String difficulty,
                                             Set<String> seen, String defaultLanguage) {
        String lang = defaultLanguage != null ? defaultLanguage : "javascript";
        int added = 0;
        for (Object[] fb : CODING_FALLBACK_PROBLEMS) {
            if (added >= needed) break;
            String text = (String) fb[0];
            if (TextSimilarity.isDuplicate(text, seen, DEDUP_THRESHOLD)) continue;
            Question q = Question.builder()
                    .interview(interview)
                    .questionText(text)
                    .title((String) fb[1])
                    .problemDescription((String) fb[2])
                    .exampleInput((String) fb[3])
                    .exampleOutput((String) fb[4])
                    .constraints((String) fb[5])
                    .starterCode((String) fb[6])
                    .tags((String) fb[7])
                    .timeComplexity((String) fb[8])
                    .type("coding")
                    .isCodeQuestion(true)
                    .codeType("write")
                    .codeLanguage(lang)
                    .difficulty(difficulty)
                    .generatedByAI(false)
                    .build();
            accepted.add(q);
            seen.add(TextSimilarity.normalize(text));
            added++;
        }
    }


    private String fallbackIntro(String role, String structuredProfile) {
        String r = role != null ? role.toLowerCase() : "";
        String project = jsonFirstArrayItem(structuredProfile, "projects");
        String skill = jsonFirstArrayItem(structuredProfile, "skills");
        if (r.contains("java")) return "Tell me about your experience building backend applications using Java and Spring Boot.";
        if (r.contains("frontend") || r.contains("react")) return "Walk me through the most challenging frontend project listed on your resume.";
        if (r.contains("ai") || r.contains("ml") || r.contains("data") || r.contains("machine")) return "Explain the AI or data project you are most proud of and the challenges you solved.";
        if (project != null) return "Walk me through " + project + " listed on your resume and the key challenges you solved.";
        if (skill != null) return "Tell me about your experience with " + skill + " as a " + (role != null ? role : "software") + " professional.";
        return "Tell me about your background and what excites you about the " + (role != null ? role : "software") + " role.";
    }

    private String jsonFirstArrayItem(String json, String key) {
        if (json == null) return null;
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map) {
                Object arr = ((Map<?, ?>) parsed).get(key);
                if (arr instanceof List && !((List<?>) arr).isEmpty()) {
                    return String.valueOf(((List<?>) arr).get(0));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
