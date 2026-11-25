package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.MasteryScore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 知识点掌握度 Mapper（M5）
 * 更新全部走单语句原子 upsert（ON DUPLICATE KEY UPDATE），total 在 SQL 内按
 * pending_retest 分支折算，禁止读改写，避免并发丢失更新
 */
public interface MasteryScoreMapper extends BaseMapper<MasteryScore> {

    /**
     * 视频步骤完成标记（幂等）：video_done 置 1 后按既有 pending_retest 分支重算 total。
     * 折算口径（与实体注释一致）：待复测 total = videoDone*40 + accuracy*0.6（即 20/50、30/50 折算）；
     * 已复测 total = videoDone*20 + accuracy*0.3 + retest*0.5。
     * 无映射视频的任务按"视同完成"调用本方法。
     */
    @Insert("""
            INSERT INTO mastery_scores
              (user_id, knowledge_point_id, video_done, pending_retest, total, create_time, update_time)
            VALUES (#{userId}, #{pointId}, 1, 1, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              video_done = 1,
              total = CASE WHEN pending_retest = 1
                           THEN 40 + IFNULL(practice_accuracy, 0) * 0.6
                           ELSE 20 + IFNULL(practice_accuracy, 0) * 0.3 + IFNULL(retest_score, 0) * 0.5 END,
              update_time = CURRENT_TIMESTAMP
            """)
    int upsertVideoDone(@Param("userId") long userId, @Param("pointId") long pointId);

    /**
     * 练习/变式提交后联动最近一次正确率（0-100）：按既有 pending_retest 分支重算 total。
     * 待复测 total = video_done*40 + accuracy*0.6；已复测 total = video_done*20 + accuracy*0.3 + retest*0.5。
     * 新插入行（首练）video_done=0、pending_retest=1、total = accuracy*0.6。
     */
    @Insert("""
            INSERT INTO mastery_scores
              (user_id, knowledge_point_id, video_done, practice_accuracy, pending_retest,
               total, create_time, update_time)
            VALUES (#{userId}, #{pointId}, 0, #{accuracy}, 1,
                    #{accuracy} * 0.6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              practice_accuracy = #{accuracy},
              total = CASE WHEN pending_retest = 1
                           THEN video_done * 40 + #{accuracy} * 0.6
                           ELSE video_done * 20 + #{accuracy} * 0.3 + IFNULL(retest_score, 0) * 0.5 END,
              update_time = CURRENT_TIMESTAMP
            """)
    int upsertPracticeAccuracy(@Param("userId") long userId, @Param("pointId") long pointId,
                               @Param("accuracy") BigDecimal accuracy);

    /**
     * 复测提交后联动最近一次复测得分（0-100）：置 retest_score、pending_retest=0、last_retest_time，
     * total = video_done*20 + accuracy*0.3 + score*0.5。新插入行（跳过视频直接复测）video_done=0。
     */
    @Insert("""
            INSERT INTO mastery_scores
              (user_id, knowledge_point_id, video_done, retest_score, pending_retest,
               last_retest_time, total, create_time, update_time)
            VALUES (#{userId}, #{pointId}, 0, #{score}, 0,
                    CURRENT_TIMESTAMP, #{score} * 0.5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              retest_score = #{score},
              pending_retest = 0,
              last_retest_time = CURRENT_TIMESTAMP,
              total = video_done * 20 + IFNULL(practice_accuracy, 0) * 0.3 + #{score} * 0.5,
              update_time = CURRENT_TIMESTAMP
            """)
    int upsertRetestScore(@Param("userId") long userId, @Param("pointId") long pointId,
                          @Param("score") BigDecimal score);
}
