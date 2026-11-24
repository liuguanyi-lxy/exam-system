CREATE TABLE ai_exam_reports (
    exam_record_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    lease_token CHAR(36) NULL,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    report_json LONGTEXT NULL,
    reviewed_by BIGINT NULL,
    review_note VARCHAR(1000) NULL,
    reviewed_at DATETIME NULL,
    PRIMARY KEY (exam_record_id),
    KEY idx_report_work (status, available_at, exam_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_question_chats (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    exam_record_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    request_id CHAR(36) NOT NULL,
    round_no INT NOT NULL,
    message VARCHAR(2000) NOT NULL,
    answer TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    lease_token CHAR(36) NULL,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    model_version VARCHAR(64) NULL,
    prompt_version VARCHAR(64) NULL,
    review_status VARCHAR(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_request (exam_record_id, question_id, request_id),
    UNIQUE KEY uk_chat_round (exam_record_id, question_id, round_no),
    KEY idx_chat_work (status, available_at, id),
    KEY idx_chat_list (exam_record_id, question_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
