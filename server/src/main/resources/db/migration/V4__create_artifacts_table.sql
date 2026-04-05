CREATE TABLE artifacts (
    id BIGSERIAL PRIMARY KEY,
    job_run_id BIGINT NOT NULL,
    pattern VARCHAR(500) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_artifact_job_run FOREIGN KEY (job_run_id) REFERENCES job_runs(id)
);
