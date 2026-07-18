-- Allow game-level comments not tied to a specific performance
ALTER TABLE comment ALTER COLUMN performance_id DROP NOT NULL;
