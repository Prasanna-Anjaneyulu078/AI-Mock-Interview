-- V10__remove_full_stack_role.sql

-- 1. Migrate any existing interviews from FULL_STACK_DEVELOPER to JAVA_BACKEND_DEVELOPER
UPDATE interviews
SET interview_type = 'JAVA_BACKEND_DEVELOPER'
WHERE interview_type = 'FULL_STACK_DEVELOPER';

-- 2. Delete coding questions for FULL_STACK_DEVELOPER
DELETE FROM coding_question_bank
WHERE role = 'FULL_STACK_DEVELOPER';

-- 3. Delete standard questions for FULL_STACK_DEVELOPER
DELETE FROM question_bank
WHERE role = 'FULL_STACK_DEVELOPER';

-- 4. Delete role metadata for FULL_STACK_DEVELOPER
DELETE FROM role_metadata
WHERE role_name = 'FULL_STACK_DEVELOPER';
