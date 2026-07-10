-- Migration: add achievements + domains-of-expertise to the structured resume profile.
-- Applies on PostgreSQL. (Hibernate ddl-auto=update also creates these from the
-- Resume entity; this script is the explicit, reviewable schema change.)

ALTER TABLE resumes ADD COLUMN IF NOT EXISTS achievements TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS domains_of_expertise TEXT;
