-- Migration: structured resume profile + interview.resume_id
-- Applies on PostgreSQL. (Hibernate ddl-auto=update will also create these,
-- but this script is the explicit, reviewable schema change.)

-- 1) Dedicated structured-profile columns on resumes (replaces reliance on the
--    single structured_skills JSON blob for querying/storage).
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS skills TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS technologies TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS frameworks TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS languages TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS projects TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS education TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS experience TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS certifications TEXT;

-- 2) Link interviews back to the resume used (enables same-resume question dedup).
ALTER TABLE interviews ADD COLUMN IF NOT EXISTS resume_id BIGINT;

-- (Optional) Enforce referential integrity once data is clean:
-- ALTER TABLE interviews
--   ADD CONSTRAINT fk_interviews_resume FOREIGN KEY (resume_id) REFERENCES resumes(id);
