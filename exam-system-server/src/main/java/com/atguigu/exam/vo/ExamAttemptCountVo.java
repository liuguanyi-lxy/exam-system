package com.atguigu.exam.vo;

import lombok.Data;

@Data
public class ExamAttemptCountVo {
    private Long examId;
    private long usedAttempts;
}
