-- Add new score categories for Voice Optimization Redesign
ALTER TABLE answers 
ADD COLUMN IF NOT EXISTS project_score DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS confidence_score DOUBLE PRECISION;
