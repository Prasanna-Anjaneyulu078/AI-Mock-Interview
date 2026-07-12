import json
import random

roles = {
    'JAVA_BACKEND_DEVELOPER': ['Core Java', 'Spring Boot', 'Microservices', 'REST APIs', 'Hibernate'],
    'PYTHON_DEVELOPER': ['Python Fundamentals', 'Flask', 'Django', 'FastAPI', 'Decorators'],
    'FRONTEND_DEVELOPER': ['React', 'JavaScript', 'CSS', 'HTML', 'State Management'],
    'FULL_STACK_DEVELOPER': ['React', 'Spring Boot', 'Databases', 'Authentication', 'Deployment'],
    'CS_FUNDAMENTALS': ['DBMS', 'Operating Systems', 'Networking', 'OOP', 'Data Structures'],
    'SYSTEM_DESIGN': ['Scalability', 'Load Balancing', 'Microservices', 'Database Design', 'Caching'],
    'DEVOPS_ENGINEER': ['Docker', 'Kubernetes', 'CI/CD', 'AWS', 'Linux'],
    'DATA_ANALYST': ['SQL', 'Python', 'Data Visualization', 'Statistics', 'Power BI'],
    'AI_ML_ENGINEER': ['Machine Learning', 'Deep Learning', 'NLP', 'TensorFlow', 'PyTorch']
}

difficulties = ['EASY', 'MEDIUM', 'HARD']
categories = ['technical', 'behavioral', 'project']

question_templates = [
    "Explain the concept of {topic} and how it applies to modern development.",
    "What are the trade-offs when using {topic}?",
    "How would you optimize an application that relies heavily on {topic}?",
    "Describe a time you encountered a challenging bug related to {topic}.",
    "What are the best practices for implementing {topic}?",
    "Can you provide an example of when NOT to use {topic}?",
    "How does {topic} differ from alternative approaches?",
    "What is the underlying mechanism behind {topic}?",
    "Explain how {topic} improves system scalability.",
    "Discuss the security implications of {topic}."
]

coding_templates = [
    ("Implement a feature using {topic}", "Write a function that demonstrates {topic}.", "1 <= n <= 100", "function doSomething() {{}}", "function doSomething() {{ return true; }}", "O(n)", "O(1)"),
    ("Optimize this {topic} logic", "Given a slow implementation of {topic}, optimize it.", "0 <= x <= 1000", "function optimize() {{}}", "function optimize() {{ return x * 2; }}", "O(1)", "O(1)"),
    ("Build a simple {topic} parser", "Parse a string using {topic} principles.", "length <= 100", "function parse() {{}}", "function parse() {{ return []; }}", "O(n)", "O(n)"),
    ("Design a {topic} state machine", "Implement state transitions for {topic}.", "states <= 10", "function stateMachine() {{}}", "function stateMachine() {{ return 1; }}", "O(1)", "O(1)"),
    ("Reverse a {topic} data structure", "Reverse the structure related to {topic}.", "size <= 1000", "function reverse() {{}}", "function reverse() {{ return null; }}", "O(n)", "O(n)")
]

with open('backend/src/main/resources/db/migration/V8__question_bank.sql', 'w') as f:
    f.write("-- V8__question_bank.sql\n\n")
    f.write("CREATE TABLE question_bank (\n")
    f.write("    id BIGSERIAL PRIMARY KEY,\n")
    f.write("    role VARCHAR(255) NOT NULL,\n")
    f.write("    difficulty VARCHAR(255) NOT NULL,\n")
    f.write("    category VARCHAR(255) NOT NULL,\n")
    f.write("    question_text TEXT NOT NULL,\n")
    f.write("    expected_answer TEXT,\n")
    f.write("    is_active BOOLEAN DEFAULT TRUE,\n")
    f.write("    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP\n")
    f.write(");\n\n")
    f.write("INSERT INTO question_bank (role, difficulty, category, question_text, expected_answer) VALUES\n")
    
    values = []
    for role, topics in roles.items():
        for diff in difficulties:
            for i in range(10):
                topic = random.choice(topics)
                template = random.choice(question_templates)
                q_text = template.format(topic=topic).replace("'", "''")
                ans_text = f"An ideal answer should discuss {topic} in depth, highlighting its core principles and demonstrating practical experience.".replace("'", "''")
                cat = random.choice(categories)
                values.append(f"('{role}', '{diff}', '{cat}', '{q_text}', '{ans_text}')")
    
    f.write(",\n".join(values) + ";\n")

with open('backend/src/main/resources/db/migration/V9__coding_question_bank.sql', 'w') as f:
    f.write("-- V9__coding_question_bank.sql\n\n")
    f.write("CREATE TABLE coding_question_bank (\n")
    f.write("    id BIGSERIAL PRIMARY KEY,\n")
    f.write("    title VARCHAR(255) NOT NULL,\n")
    f.write("    description TEXT NOT NULL,\n")
    f.write("    difficulty VARCHAR(255) NOT NULL,\n")
    f.write("    role VARCHAR(255) NOT NULL,\n")
    f.write("    language VARCHAR(255),\n")
    f.write("    constraints TEXT,\n")
    f.write("    starter_code TEXT,\n")
    f.write("    solution TEXT,\n")
    f.write("    test_cases TEXT,\n")
    f.write("    hidden_test_cases TEXT,\n")
    f.write("    expected_time_complexity VARCHAR(255),\n")
    f.write("    expected_space_complexity VARCHAR(255),\n")
    f.write("    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP\n")
    f.write(");\n\n")
    f.write("INSERT INTO coding_question_bank (title, description, difficulty, role, language, constraints, starter_code, solution, expected_time_complexity, expected_space_complexity) VALUES\n")
    
    values = []
    for role, topics in roles.items():
        for diff in difficulties:
            for i in range(10):
                topic = random.choice(topics)
                template = random.choice(coding_templates)
                title = template[0].format(topic=topic).replace("'", "''")
                desc = template[1].format(topic=topic).replace("'", "''")
                constraints = template[2]
                starter = template[3]
                sol = template[4]
                tc = template[5]
                sc = template[6]
                values.append(f"('{title}', '{desc}', '{diff}', '{role}', 'javascript', '{constraints}', '{starter}', '{sol}', '{tc}', '{sc}')")
    
    f.write(",\n".join(values) + ";\n")

print("Generated V8 and V9 migration files with 270 questions each.")
