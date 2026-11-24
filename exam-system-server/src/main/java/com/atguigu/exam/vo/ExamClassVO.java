package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
/** Request and response models for the M3 assignment-management API. */
public final class ExamClassVO {

    private ExamClassVO() {
    }

    @Data
    @Schema(description = "创建考试分配请求")
    public static class CreateRequest {
        @NotNull
        @Positive
        private Long examId;

        @NotEmpty
        @Size(max = 100)
        private List<@NotNull @Positive Long> classIds;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime startTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime endTime;

        @Min(1)
        private Integer maxAttempts = 1;
    }

    @Schema(description = "更新考试分配请求；时间字段显式 null 表示清除该侧限制")
    public static class UpdateRequest {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer maxAttempts;
        @JsonIgnore
        private boolean startTimeProvided;
        @JsonIgnore
        private boolean endTimeProvided;
        @JsonIgnore
        private boolean maxAttemptsProvided;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
            this.startTimeProvided = true;
        }

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        public LocalDateTime getEndTime() {
            return endTime;
        }

        public void setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
            this.endTimeProvided = true;
        }

        @Min(1)
        public Integer getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(Integer maxAttempts) {
            this.maxAttempts = maxAttempts;
            this.maxAttemptsProvided = true;
        }

        @JsonIgnore
        public boolean isStartTimeProvided() {
            return startTimeProvided;
        }

        @JsonIgnore
        public boolean isEndTimeProvided() {
            return endTimeProvided;
        }

        @JsonIgnore
        public boolean isMaxAttemptsProvided() {
            return maxAttemptsProvided;
        }
    }

    @Data
    @Schema(description = "班级学员考试进度")
    public static class StudentProgress {
        private Long userId;
        private String realName;
        private Integer attemptCount;
        private Integer maxScore;
        private String latestStatus;
    }

    @Data
    @Schema(description = "分配创建结果")
    public static class CreateResult {
        private Long classId;
        private Long assignmentId;
        @Schema(allowableValues = {"CREATED", "EXISTS", "REVIVED", "FAILED"})
        private String status;
        private String message;
    }

    @Data
    @Schema(description = "考试分配详情与列表项")
    public static class Detail {
        private Long id;
        private Long examId;
        private Long classId;
        private String paperName;
        private String examName;
        private String className;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime startTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime endTime;
        private Integer maxAttempts;
        private String status;
        private Integer notStartedCount;
        private Integer inProgressCount;
        private Integer submittedCount;
        private Integer gradedCount;
    }
}
