-- V1__initial_schema.sql

CREATE TABLE interview_modes (
    id BIGSERIAL PRIMARY KEY,
    mode_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    profile_image VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    resume_text TEXT,
    structured_skills TEXT,
    skills TEXT,
    technologies TEXT,
    frameworks TEXT,
    languages TEXT,
    projects TEXT,
    education TEXT,
    experience TEXT,
    certifications TEXT,
    achievements TEXT,
    domains_of_expertise TEXT,
    uploaded_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE interviews (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    interview_type VARCHAR(255),
    interview_mode VARCHAR(255),
    coding_language VARCHAR(255),
    selected_interests VARCHAR(255),
    difficulty VARCHAR(255),
    adapted_difficulty VARCHAR(255),
    running_score DOUBLE PRECISION,
    status VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION,
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    resume_text TEXT,
    target_question_count INTEGER,
    total_questions INTEGER,
    current_question INTEGER,
    feedback TEXT,
    last_audio TEXT,
    voice_enabled BOOLEAN,
    voice_name VARCHAR(255),
    voice_speed DOUBLE PRECISION,
    voice_id VARCHAR(255),
    style VARCHAR(255),
    resume_id BIGINT,
    fallback_activated BOOLEAN,
    ai_provider_used VARCHAR(255),
    provider_error VARCHAR(255),
    interview_source VARCHAR(255),
    CONSTRAINT fk_interviews_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_interview_user ON interviews(user_id);

CREATE TABLE interview_histories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    interview_id BIGINT,
    total_score DOUBLE PRECISION,
    strengths TEXT,
    improvements TEXT,
    strong_skills TEXT,
    weak_skills TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_history_interview FOREIGN KEY (interview_id) REFERENCES interviews(id)
);

CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    question_text TEXT,
    expected_answer TEXT,
    explanation TEXT,
    difficulty VARCHAR(255),
    sequence INTEGER,
    generated_byai BOOLEAN,
    type VARCHAR(255),
    is_code_question BOOLEAN,
    is_follow_up BOOLEAN,
    parent_question_id BIGINT,
    code_type VARCHAR(255),
    code_snippet TEXT,
    code_language VARCHAR(255),
    title VARCHAR(255),
    problem_description TEXT,
    example_input TEXT,
    example_output TEXT,
    constraints TEXT,
    starter_code TEXT,
    solution_code TEXT,
    tags VARCHAR(255),
    time_complexity VARCHAR(255),
    CONSTRAINT fk_questions_interview FOREIGN KEY (interview_id) REFERENCES interviews(id)
);

CREATE TABLE answers (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    answer_text TEXT,
    transcription_text TEXT,
    evaluation_score DOUBLE PRECISION,
    technical_score DOUBLE PRECISION,
    communication_score DOUBLE PRECISION,
    problem_solving_score DOUBLE PRECISION,
    code_quality_score DOUBLE PRECISION,
    project_score DOUBLE PRECISION,
    confidence_score DOUBLE PRECISION,
    fluency_score DOUBLE PRECISION,
    filler_words_count INTEGER,
    speaking_speed DOUBLE PRECISION,
    response_time_seconds INTEGER,
    difficulty_level VARCHAR(255),
    strengths TEXT,
    weaknesses TEXT,
    recommendations TEXT,
    feedback TEXT,
    improvement_suggestions TEXT,
    answer_comparison TEXT,
    code_language VARCHAR(255),
    recording_url VARCHAR(255),
    audio_duration DOUBLE PRECISION,
    code_execution_result VARCHAR(255),
    evaluation_status VARCHAR(255),
    answered_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE coding_questions (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    constraints TEXT,
    difficulty VARCHAR(255),
    starter_code TEXT,
    solution_code TEXT,
    language_support VARCHAR(255),
    time_limit INTEGER,
    memory_limit INTEGER,
    CONSTRAINT fk_coding_questions_interview FOREIGN KEY (interview_id) REFERENCES interviews(id)
);

CREATE TABLE coding_test_cases (
    id BIGSERIAL PRIMARY KEY,
    coding_question_id BIGINT NOT NULL,
    name VARCHAR(255),
    input TEXT,
    expected_output TEXT,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_coding_tc_question FOREIGN KEY (coding_question_id) REFERENCES coding_questions(id)
);

CREATE TABLE coding_submissions (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    coding_question_id BIGINT NOT NULL,
    source_code TEXT NOT NULL,
    language VARCHAR(255) NOT NULL,
    execution_time DOUBLE PRECISION,
    memory_usage DOUBLE PRECISION,
    status VARCHAR(255),
    submitted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_coding_sub_interview FOREIGN KEY (interview_id) REFERENCES interviews(id),
    CONSTRAINT fk_coding_sub_question FOREIGN KEY (coding_question_id) REFERENCES coding_questions(id)
);

CREATE TABLE coding_results (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL UNIQUE,
    passed_tests INTEGER,
    failed_tests INTEGER,
    total_tests INTEGER,
    compile_output TEXT,
    stdout TEXT,
    stderr TEXT,
    time_limit_exceeded BOOLEAN,
    memory_limit_exceeded BOOLEAN,
    compilation_error BOOLEAN,
    runtime_error BOOLEAN,
    code_quality_score DOUBLE PRECISION,
    time_complexity_score DOUBLE PRECISION,
    space_complexity_score DOUBLE PRECISION,
    style_score DOUBLE PRECISION,
    final_score DOUBLE PRECISION,
    strengths TEXT,
    weaknesses TEXT,
    optimization_suggestions TEXT,
    CONSTRAINT fk_coding_results_submission FOREIGN KEY (submission_id) REFERENCES coding_submissions(id)
);

CREATE TABLE code_submissions (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    code TEXT,
    language VARCHAR(255),
    feedback TEXT,
    stdout TEXT,
    stderr TEXT,
    execution_time DOUBLE PRECISION,
    memory_usage DOUBLE PRECISION,
    passed BOOLEAN,
    passed_tests INTEGER,
    total_tests INTEGER,
    status VARCHAR(255),
    compile_output TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_code_sub_interview FOREIGN KEY (interview_id) REFERENCES interviews(id)
);

CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    role VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_messages_interview FOREIGN KEY (interview_id) REFERENCES interviews(id)
);

CREATE TABLE user_interests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    interest_name VARCHAR(255) NOT NULL,
    CONSTRAINT fk_user_interests_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE interview_recordings (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT,
    question_id BIGINT,
    user_id BIGINT,
    audio_url VARCHAR(255),
    duration_seconds INTEGER,
    file_size_bytes BIGINT,
    transcript TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE test_cases (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    name VARCHAR(255),
    input TEXT,
    expected_output TEXT,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_test_cases_question FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE coding_scores (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    test_case_score DOUBLE PRECISION,
    code_quality_score DOUBLE PRECISION,
    time_complexity_score DOUBLE PRECISION,
    space_complexity_score DOUBLE PRECISION,
    coding_style_score DOUBLE PRECISION,
    total_score DOUBLE PRECISION,
    strengths TEXT,
    weaknesses TEXT,
    optimization_suggestions TEXT,
    CONSTRAINT fk_coding_scores_submission FOREIGN KEY (submission_id) REFERENCES coding_submissions(id)
);
