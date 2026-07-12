import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GenerateSeeds {

    public static void main(String[] args) throws IOException {
        Map<String, List<String>> roles = Map.of(
            "JAVA_BACKEND_DEVELOPER", Arrays.asList("Core Java", "Spring Boot", "Microservices", "REST APIs", "Hibernate"),
            "PYTHON_DEVELOPER", Arrays.asList("Python Fundamentals", "Flask", "Django", "FastAPI", "Decorators"),
            "FRONTEND_DEVELOPER", Arrays.asList("React", "JavaScript", "CSS", "HTML", "State Management"),
            "CS_FUNDAMENTALS", Arrays.asList("DBMS", "Operating Systems", "Networking", "OOP", "Data Structures"),
            "SYSTEM_DESIGN", Arrays.asList("Scalability", "Load Balancing", "Microservices", "Database Design", "Caching"),
            "DEVOPS_ENGINEER", Arrays.asList("Docker", "Kubernetes", "CI/CD", "AWS", "Linux"),
            "DATA_ANALYST", Arrays.asList("SQL", "Python", "Data Visualization", "Statistics", "Power BI"),
            "AI_ML_ENGINEER", Arrays.asList("Machine Learning", "Deep Learning", "NLP", "TensorFlow", "PyTorch")
        );

        String[] difficulties = {"EASY", "MEDIUM", "HARD"};
        String[] categories = {"technical", "behavioral", "project"};

        String[] questionTemplates = {
            "Explain the concept of %s and how it applies to modern development.",
            "What are the trade-offs when using %s?",
            "How would you optimize an application that relies heavily on %s?",
            "Describe a time you encountered a challenging bug related to %s.",
            "What are the best practices for implementing %s?",
            "Can you provide an example of when NOT to use %s?",
            "How does %s differ from alternative approaches?",
            "What is the underlying mechanism behind %s?",
            "Explain how %s improves system scalability.",
            "Discuss the security implications of %s."
        };

        String[][] codingTemplates = {
            {"Implement a feature using %s", "Write a function that demonstrates %s.", "1 <= n <= 100", "function doSomething() {}", "function doSomething() { return true; }", "O(n)", "O(1)"},
            {"Optimize this %s logic", "Given a slow implementation of %s, optimize it.", "0 <= x <= 1000", "function optimize() {}", "function optimize() { return x * 2; }", "O(1)", "O(1)"},
            {"Build a simple %s parser", "Parse a string using %s principles.", "length <= 100", "function parse() {}", "function parse() { return []; }", "O(n)", "O(n)"},
            {"Design a %s state machine", "Implement state transitions for %s.", "states <= 10", "function stateMachine() {}", "function stateMachine() { return 1; }", "O(1)", "O(1)"},
            {"Reverse a %s data structure", "Reverse the structure related to %s.", "size <= 1000", "function reverse() {}", "function reverse() { return null; }", "O(n)", "O(n)"}
        };

        Random random = new Random();

        try (FileWriter fw = new FileWriter("backend/src/main/resources/db/migration/V8__question_bank.sql")) {
            fw.write("-- V8__question_bank.sql\n\n");
            fw.write("CREATE TABLE question_bank (\n");
            fw.write("    id BIGSERIAL PRIMARY KEY,\n");
            fw.write("    role VARCHAR(255) NOT NULL,\n");
            fw.write("    difficulty VARCHAR(255) NOT NULL,\n");
            fw.write("    category VARCHAR(255) NOT NULL,\n");
            fw.write("    question_text TEXT NOT NULL,\n");
            fw.write("    expected_answer TEXT,\n");
            fw.write("    is_active BOOLEAN DEFAULT TRUE,\n");
            fw.write("    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP\n");
            fw.write(");\n\n");
            fw.write("INSERT INTO question_bank (role, difficulty, category, question_text, expected_answer) VALUES\n");
            
            StringBuilder values = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, List<String>> entry : roles.entrySet()) {
                String role = entry.getKey();
                List<String> topics = entry.getValue();
                for (String diff : difficulties) {
                    for (int i = 0; i < 10; i++) {
                        String topic = topics.get(random.nextInt(topics.size()));
                        String template = questionTemplates[random.nextInt(questionTemplates.length)];
                        String qText = String.format(template, topic).replace("'", "''");
                        String ansText = String.format("An ideal answer should discuss %s in depth.", topic).replace("'", "''");
                        String cat = categories[random.nextInt(categories.length)];
                        
                        if (!first) values.append(",\n");
                        values.append(String.format("('%s', '%s', '%s', '%s', '%s')", role, diff, cat, qText, ansText));
                        first = false;
                    }
                }
            }
            fw.write(values.toString() + ";\n");
        }

        try (FileWriter fw = new FileWriter("backend/src/main/resources/db/migration/V9__coding_question_bank.sql")) {
            fw.write("-- V9__coding_question_bank.sql\n\n");
            fw.write("CREATE TABLE coding_question_bank (\n");
            fw.write("    id BIGSERIAL PRIMARY KEY,\n");
            fw.write("    title VARCHAR(255) NOT NULL,\n");
            fw.write("    description TEXT NOT NULL,\n");
            fw.write("    difficulty VARCHAR(255) NOT NULL,\n");
            fw.write("    role VARCHAR(255) NOT NULL,\n");
            fw.write("    language VARCHAR(255),\n");
            fw.write("    constraints TEXT,\n");
            fw.write("    starter_code TEXT,\n");
            fw.write("    solution TEXT,\n");
            fw.write("    test_cases TEXT,\n");
            fw.write("    hidden_test_cases TEXT,\n");
            fw.write("    expected_time_complexity VARCHAR(255),\n");
            fw.write("    expected_space_complexity VARCHAR(255),\n");
            fw.write("    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP\n");
            fw.write(");\n\n");
            fw.write("INSERT INTO coding_question_bank (title, description, difficulty, role, language, constraints, starter_code, solution, expected_time_complexity, expected_space_complexity) VALUES\n");
            
            StringBuilder values = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, List<String>> entry : roles.entrySet()) {
                String role = entry.getKey();
                List<String> topics = entry.getValue();
                for (String diff : difficulties) {
                    for (int i = 0; i < 10; i++) {
                        String topic = topics.get(random.nextInt(topics.size()));
                        String[] template = codingTemplates[random.nextInt(codingTemplates.length)];
                        String title = String.format(template[0], topic).replace("'", "''");
                        String desc = String.format(template[1], topic).replace("'", "''");
                        String constraints = template[2];
                        String starter = template[3];
                        String sol = template[4];
                        String tc = template[5];
                        String sc = template[6];
                        
                        if (!first) values.append(",\n");
                        values.append(String.format("('%s', '%s', '%s', '%s', 'javascript', '%s', '%s', '%s', '%s', '%s')", 
                                title, desc, diff, role, constraints, starter, sol, tc, sc));
                        first = false;
                    }
                }
            }
            fw.write(values.toString() + ";\n");
        }
        
        System.out.println("Generated V8 and V9 migration files with 270 questions each.");
    }
}
