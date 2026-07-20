-- Song length fetched from YouTube Data API at queue time (null when unavailable)
ALTER TABLE performance ADD COLUMN duration_seconds bigint NULL;

-- Deadline for a RUNNING performance to auto-lock even if not all judges submit
ALTER TABLE performance ADD COLUMN judging_deadline_at timestamptz NULL;
