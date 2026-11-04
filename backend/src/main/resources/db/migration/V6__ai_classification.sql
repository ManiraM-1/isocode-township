ALTER TABLE complaint ADD COLUMN ai_reason VARCHAR(300);

CREATE UNIQUE INDEX escalation_one_per_complaint ON escalation (complaint_id);
