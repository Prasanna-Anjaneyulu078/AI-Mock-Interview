-- Phase 8: Database Improvements
-- Add missing index and constraint improvements

-- Ensure code_submissions has question_id link
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='code_submissions' AND column_name='question_id'
    ) THEN
        ALTER TABLE code_submissions ADD COLUMN question_id BIGINT REFERENCES questions(id);
    END IF;
END $$;

-- Add skipped flag to answers for explicit question-skipping (Phase 3)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='answers' AND column_name='skipped'
    ) THEN
        ALTER TABLE answers ADD COLUMN skipped BOOLEAN DEFAULT FALSE;
    END IF;
END $$;

-- Add score breakdown fields to answers for Phase 6 weighted formula
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='answers' AND column_name='complexity_score'
    ) THEN
        ALTER TABLE answers ADD COLUMN complexity_score DOUBLE PRECISION;
        ALTER TABLE answers ADD COLUMN optimization_score DOUBLE PRECISION;
    END IF;
END $$;

-- Index for performance: look up answers by evaluation score for analytics
CREATE INDEX IF NOT EXISTS idx_answer_eval_score ON answers(evaluation_score);

-- Index for code_submissions query by interview + question
CREATE INDEX IF NOT EXISTS idx_code_submission_interview ON code_submissions(interview_id);
CREATE INDEX IF NOT EXISTS idx_code_submission_question ON code_submissions(question_id);

-- Ensure questions have proper index on type for filtering
CREATE INDEX IF NOT EXISTS idx_question_type ON questions(type);
CREATE INDEX IF NOT EXISTS idx_question_is_code ON questions(is_code_question);
