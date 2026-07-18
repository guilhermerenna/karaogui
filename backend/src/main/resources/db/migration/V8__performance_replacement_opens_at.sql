-- Add replacement_opens_at to performance for the volunteer window timer
ALTER TABLE performance ADD COLUMN replacement_opens_at timestamptz NULL;

-- Add submitted_at to judge_assignment (tracks when a judge submitted their evaluation)
ALTER TABLE judge_assignment ADD COLUMN submitted_at timestamptz NULL;

-- Add version (optimistic locking) to performer_slot
ALTER TABLE performer_slot ADD COLUMN version bigint NOT NULL DEFAULT 0;
