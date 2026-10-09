CREATE TABLE region_demo_review (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL UNIQUE REFERENCES raw_dataset_record(id) ON DELETE RESTRICT,
    branch_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_REVIEW',
    version INTEGER NOT NULL DEFAULT 1,
    submitted_by BIGINT NOT NULL REFERENCES app_user(id),
    reviewed_by BIGINT REFERENCES app_user(id),
    reason TEXT,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_region_demo_review_status CHECK (status IN ('IN_REVIEW', 'APPROVED', 'RETURNED'))
);

CREATE INDEX idx_region_demo_review_branch_status ON region_demo_review(branch_id, status);
