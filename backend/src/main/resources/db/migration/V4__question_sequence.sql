-- Migration: stable question sequence for adaptive follow-up insertion (spec #4, #5).
-- Applies on PostgreSQL. (Hibernate ddl-auto=update also creates this from the
-- Question entity; this script is the explicit, reviewable schema change.)

ALTER TABLE questions ADD COLUMN IF NOT EXISTS sequence INTEGER;

-- Backfill legacy rows (dev only) so ordering is contiguous: sequence = id order.
-- UPDATE questions q SET sequence = sub.rn
-- FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM questions) sub
-- WHERE q.id = sub.id AND q.sequence IS NULL;
