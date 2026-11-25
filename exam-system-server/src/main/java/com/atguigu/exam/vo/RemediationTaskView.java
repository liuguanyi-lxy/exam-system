package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 补课任务链视图（M5 学员端）
 *
 * Summary 由 mapper SQL 直接映射（JOIN 知识点名与掌握度；overdue 在 SQL 内按 DB 时间
 * 计算，云端 DB 为 UTC，禁止 JVM now() 对比）；Detail 在其上叠加四步状态、快照策略与视频列表。
 */
public final class RemediationTaskView {

    /** 任务列表/详情通用摘要（masteryTotal/pendingRetest 为 null 表示尚未产生掌握度记录） */
    public record Summary(long id, Long knowledgePointId, String knowledgePointName, String status,
                          BigDecimal masteryTotal, Boolean pendingRetest,
                          @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime dueTime,
                          boolean overdue) {}

    /** 推荐视频项：completed 复用 M2 learning_video_progress.status='COMPLETED' 口径 */
    public record VideoItem(long videoId, String title, boolean completed) {}

    /** 步骤状态机：video = NO_VIDEO（无可推荐视频）/TODO/DONE；gateOpen = 视频口径放行后练习·变式·复测可进入 */
    public record Steps(String video, boolean gateOpen) {}

    /** 任务详情：四步状态 + 掌握度 + 生成时策略快照 + 推荐视频列表 */
    public record Detail(long id, Long knowledgePointId, String knowledgePointName, String status,
                         BigDecimal masteryTotal, Boolean pendingRetest,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime dueTime,
                         boolean overdue, RemediationPolicy policy, Steps steps, List<VideoItem> videos) {}

    /** 视频步骤完成标记结果（幂等，重复调用返回相同掌握度） */
    public record VideoCompleteResult(BigDecimal masteryTotal, Boolean pendingRetest) {}
}
