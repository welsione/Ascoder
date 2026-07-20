CREATE TABLE agentEvent (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    questionId BIGINT       NOT NULL,
    attemptNo  INT          NOT NULL DEFAULT 1,
    sequenceNo INT          NOT NULL,
    eventType  VARCHAR(64)  NOT NULL,
    payload    MEDIUMTEXT   NOT NULL,
    createdAt  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agentEvent_question_attempt_seq UNIQUE (questionId, attemptNo, sequenceNo)
);

CREATE INDEX idx_agentEvent_question ON agentEvent (questionId, attemptNo);
