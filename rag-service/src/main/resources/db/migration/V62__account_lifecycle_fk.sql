-- M7: FK/cascade fixes for verifiable account deletion.

-- Remove regression suite rows that reference missing users before adding FK.
DELETE FROM runtime_trace_regression_suite_run r
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = r.user_id);

DELETE FROM runtime_trace_regression_suite_definition d
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = d.user_id);

ALTER TABLE evaluation_campaign
    DROP CONSTRAINT IF EXISTS evaluation_campaign_user_id_fkey;

ALTER TABLE evaluation_campaign
    ADD CONSTRAINT evaluation_campaign_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE evaluation_campaign
    DROP CONSTRAINT IF EXISTS evaluation_campaign_project_id_fkey;

ALTER TABLE evaluation_campaign
    ADD CONSTRAINT evaluation_campaign_project_id_fkey
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL;

ALTER TABLE evaluation_run
    DROP CONSTRAINT IF EXISTS evaluation_run_campaign_id_fkey;

ALTER TABLE evaluation_run
    ADD CONSTRAINT evaluation_run_campaign_id_fkey
        FOREIGN KEY (campaign_id) REFERENCES evaluation_campaign (id) ON DELETE SET NULL;

ALTER TABLE vector_store
    DROP CONSTRAINT IF EXISTS vector_store_project_id_fkey;

ALTER TABLE vector_store
    ADD CONSTRAINT vector_store_project_id_fkey
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE runtime_trace_regression_suite_definition
    DROP CONSTRAINT IF EXISTS fk_rt_regr_suite_def_user;

ALTER TABLE runtime_trace_regression_suite_definition
    ADD CONSTRAINT fk_rt_regr_suite_def_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE runtime_trace_regression_suite_run
    DROP CONSTRAINT IF EXISTS fk_rt_regr_suite_run_user;

ALTER TABLE runtime_trace_regression_suite_run
    ADD CONSTRAINT fk_rt_regr_suite_run_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
