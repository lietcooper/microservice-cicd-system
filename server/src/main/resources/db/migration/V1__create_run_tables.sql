CREATE TABLE pipeline_runs (
    id          BIGSERIAL PRIMARY KEY,
    pipeline_name VARCHAR(255)  NOT NULL,
    run_no      INT           NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    start_time  TIMESTAMP WITH TIME ZONE,
    end_time    TIMESTAMP WITH TIME ZONE,
    git_hash    VARCHAR(255),
    git_branch  VARCHAR(255),
    git_repo    VARCHAR(512),
    CONSTRAINT uq_pipeline_run UNIQUE (pipeline_name, run_no)
);

CREATE TABLE stage_runs (
    id              BIGSERIAL PRIMARY KEY,
    pipeline_run_id BIGINT       NOT NULL,
    stage_name      VARCHAR(255) NOT NULL,
    stage_order     INT          NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    start_time      TIMESTAMP WITH TIME ZONE,
    end_time        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_stage_pipeline_run
        FOREIGN KEY (pipeline_run_id) REFERENCES pipeline_runs (id)
        ON DELETE CASCADE
);

CREATE TABLE job_runs (
    id           BIGSERIAL PRIMARY KEY,
    stage_run_id BIGINT       NOT NULL,
    job_name     VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    start_time   TIMESTAMP WITH TIME ZONE,
    end_time     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_job_stage_run
        FOREIGN KEY (stage_run_id) REFERENCES stage_runs (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_pipeline_runs_name ON pipeline_runs (pipeline_name);
CREATE INDEX idx_stage_runs_pipeline ON stage_runs (pipeline_run_id);
CREATE INDEX idx_job_runs_stage ON job_runs (stage_run_id);
