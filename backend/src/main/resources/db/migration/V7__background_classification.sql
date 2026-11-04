ALTER TABLE complaint ADD COLUMN classification_status VARCHAR(8) NOT NULL DEFAULT 'DONE'
    CHECK (classification_status IN ('PENDING', 'DONE', 'FAILED'));

CREATE INDEX complaint_classification_pending ON complaint (created_at) WHERE classification_status = 'PENDING';

ALTER TABLE complaint_event DROP CONSTRAINT complaint_event_type_check;
ALTER TABLE complaint_event ADD CONSTRAINT complaint_event_type_check
    CHECK (type IN ('CREATED', 'AUTO_ASSIGNED', 'STATUS_CHANGED', 'ASSIGNEE_CHANGED', 'COMMENTED', 'CLOSED',
                    'REOPENED', 'CATEGORY_CHANGED', 'ESCALATED', 'ESCALATION_ACKNOWLEDGED', 'LINKED', 'UNLINKED',
                    'CLASSIFIED', 'AI_SUGGESTED'));
