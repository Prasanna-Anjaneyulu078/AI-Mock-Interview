-- V7__role_metadata.sql

CREATE TABLE role_metadata (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    topics_json TEXT NOT NULL,
    icon_name VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO role_metadata (role_name, display_name, description, icon_name, topics_json) VALUES
('JAVA_BACKEND_DEVELOPER', 'Java Backend Developer', 'Core Java, Spring Boot, REST APIs, Microservices, and Databases', 'FaJava', '["Core Java", "OOP", "Collections", "Streams", "JVM", "Multithreading", "Spring Boot", "REST APIs", "Hibernate/JPA", "Security", "Microservices", "SQL"]'),
('PYTHON_DEVELOPER', 'Python Developer', 'Python fundamentals, Flask, Django, FastAPI, and Scripting', 'FaPython', '["Python Fundamentals", "OOP", "Decorators", "Generators", "Iterators", "Context Managers", "Asyncio", "Flask", "Django", "FastAPI", "SQLAlchemy", "Testing", "Performance Optimization"]'),
('FRONTEND_DEVELOPER', 'Frontend Developer', 'HTML, CSS, JavaScript, React, and UI/UX design', 'FaReact', '["HTML", "CSS", "JavaScript", "TypeScript", "React", "Hooks", "State Management", "Routing", "API Integration", "Performance Optimization"]'),
('FULL_STACK_DEVELOPER', 'Full Stack Developer', 'End-to-end web development across frontend and backend technologies', 'BsLightningFill', '["React", "JavaScript", "Java", "Spring Boot", "REST APIs", "Databases", "Authentication", "System Design Basics", "Deployment"]'),
('CS_FUNDAMENTALS', 'CS Fundamentals', 'Core Computer Science concepts including DBMS, OS, Networks, and OOP', 'BsWindowStack', '["OOP", "DBMS", "SQL", "Transactions", "Indexing", "Normalization", "Operating Systems", "Memory Management", "Scheduling", "Deadlocks", "Computer Networks", "TCP/IP", "DNS", "HTTP"]'),
('SYSTEM_DESIGN', 'System Design', 'Architectural patterns, scalable systems, HLD, and LLD', 'BsDiagram3', '["Database Design", "ER Diagrams", "Schema Design", "SQL Optimization", "LLD", "SOLID Principles", "Design Patterns", "UML", "HLD", "Scalability", "Load Balancing", "Caching", "Distributed Systems", "Microservices", "API Design"]'),
('DEVOPS_ENGINEER', 'DevOps Engineer', 'CI/CD, Cloud, Docker, Kubernetes, and Infrastructure automation', 'BsCloud', '["Linux", "Docker", "Kubernetes", "CI/CD", "AWS", "Terraform", "Monitoring"]'),
('DATA_ANALYST', 'Data Analyst', 'SQL, Python, Data Visualization, and Statistical analysis', 'BsGraphUp', '["SQL", "Excel", "Power BI", "Statistics", "Python", "Data Visualization"]'),
('AI_ML_ENGINEER', 'AI / ML Engineer', 'Machine Learning, Deep Learning, NLP, Computer Vision, and MLOps', 'BsRobot', '["Machine Learning", "Deep Learning", "NLP", "Computer Vision", "TensorFlow", "PyTorch", "MLOps", "LLMs"]');
