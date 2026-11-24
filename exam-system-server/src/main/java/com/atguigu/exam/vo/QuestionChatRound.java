package com.atguigu.exam.vo;

import java.time.LocalDateTime;

public record QuestionChatRound(String requestId, int roundNo, String message, String answer, String status,
                                String modelVersion, String promptVersion, String reviewStatus,
                                LocalDateTime createdAt) {}
