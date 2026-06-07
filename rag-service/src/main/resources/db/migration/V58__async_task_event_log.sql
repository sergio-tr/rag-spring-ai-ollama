-- Bounded event log for Lab async job SSE resume (GET /lab/jobs/{id}/events?since=eventId).

ALTER TABLE async_task ADD COLUMN IF NOT EXISTS event_log_json JSONB;
