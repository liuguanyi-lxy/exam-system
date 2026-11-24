CREATE TABLE learning_video_sessions (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    progress_id      BIGINT NOT NULL,
    chapter_video_id BIGINT NOT NULL,
    session_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_sequence    BIGINT NOT NULL DEFAULT 0,
    watched_seconds  INT NOT NULL DEFAULT 0,
    last_signal_at   DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_progress_session (progress_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE learning_video_progress
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    MODIFY COLUMN session_id VARCHAR(36) NULL COMMENT '旧版保留字段；个人会话以 learning_video_sessions 为准';
