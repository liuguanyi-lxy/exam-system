package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.RemediationTaskItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 补课步骤作答明细 Mapper（M5）
 * 练习/变式/复测的轮次推进、选题（ORDER BY RAND）与幂等提交共用。
 * 本表物理删除（实体不继承 BaseEntity），作答历史只增不改删。
 */
public interface RemediationTaskItemMapper extends BaseMapper<RemediationTaskItem> {

    /** 该任务该步骤的最大轮次；无记录返回 NULL，由调用方按 0 处理 */
    @Select("SELECT MAX(round_no) FROM remediation_task_items WHERE task_id = #{taskId} AND step = #{step}")
    Integer maxRound(@Param("taskId") long taskId, @Param("step") String step);

    /**
     * 取题即落库（user_answer 留 NULL）：幂等靠唯一键 uk_item_task_step_q_round 兜底，
     * 并发双击取题不产生双记录，重做 = round_no+1 新行。
     */
    @Insert("""
            INSERT IGNORE INTO remediation_task_items
              (task_id, step, round_no, question_id, create_time)
            VALUES (#{taskId}, #{step}, #{roundNo}, #{questionId}, CURRENT_TIMESTAMP)
            """)
    int insertIgnore(@Param("taskId") long taskId, @Param("step") String step,
                     @Param("roundNo") int roundNo, @Param("questionId") long questionId);

    /**
     * 幂等提交：WHERE user_answer IS NULL 保证重复提交不重复计分（并发下后到者更新 0 行）。
     * is_correct/score/ai_feedback 允许为 NULL（AI 判分失败按未判分落库，可稍后重做）。
     */
    @Update("""
            UPDATE remediation_task_items
            SET user_answer = #{userAnswer}, is_correct = #{isCorrect},
                score = #{score}, ai_feedback = #{aiFeedback}
            WHERE task_id = #{taskId} AND step = #{step} AND round_no = #{roundNo}
              AND question_id = #{questionId} AND user_answer IS NULL
            """)
    int submitAnswer(@Param("taskId") long taskId, @Param("step") String step, @Param("roundNo") int roundNo,
                     @Param("questionId") long questionId, @Param("userAnswer") String userAnswer,
                     @Param("isCorrect") Byte isCorrect, @Param("score") BigDecimal score,
                     @Param("aiFeedback") String aiFeedback);

    /**
     * 按知识点从题池随机选题（新轮选题口径，ORDER BY RAND）。
     * excludedIds 非空时排除已用题（变式题排除本任务全部 PRACTICE 已用题）；为空即全池（练习/复测）。
     */
    @Select("""
            <script>
            SELECT q.* FROM questions q
            JOIN question_knowledge_points qkp ON qkp.question_id = q.id
              AND qkp.knowledge_point_id = #{pointId}
            WHERE q.is_deleted = 0
            <if test="excludedIds != null and !excludedIds.isEmpty()">
              AND q.id NOT IN
              <foreach collection="excludedIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY RAND()
            LIMIT #{limit}
            </script>
            """)
    List<Question> selectPoolQuestions(@Param("pointId") long pointId,
                                       @Param("excludedIds") List<Long> excludedIds,
                                       @Param("limit") int limit);

    /** 题池可用题计数（同 selectPoolQuestions 过滤口径），用于变式题不足判定与回退 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT q.id) FROM questions q
            JOIN question_knowledge_points qkp ON qkp.question_id = q.id
              AND qkp.knowledge_point_id = #{pointId}
            WHERE q.is_deleted = 0
            <if test="excludedIds != null and !excludedIds.isEmpty()">
              AND q.id NOT IN
              <foreach collection="excludedIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            </script>
            """)
    int countPoolQuestions(@Param("pointId") long pointId, @Param("excludedIds") List<Long> excludedIds);
}
