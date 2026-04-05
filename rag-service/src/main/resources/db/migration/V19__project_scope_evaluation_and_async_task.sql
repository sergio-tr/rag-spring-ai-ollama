-- Optional project scope for batch evaluation runs and async Lab jobs (ADR 0003).

ALTER TABLE evaluation_run
    ADD COLUMN project_id UUID REFERENCES projects (id) ON DELETE SET NULL;

CREATE INDEX idx_evaluation_run_project ON evaluation_run (project_id);

ALTER TABLE async_task
    ADD COLUMN project_id UUID REFERENCES projects (id) ON DELETE SET NULL;

CREATE INDEX idx_async_task_project ON async_task (project_id);
