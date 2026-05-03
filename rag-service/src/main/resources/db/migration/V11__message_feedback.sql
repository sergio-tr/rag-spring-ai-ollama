-- User feedback on assistant messages.

CREATE TABLE message_feedback (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating INTEGER CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_message_feedback_message ON message_feedback (message_id);
