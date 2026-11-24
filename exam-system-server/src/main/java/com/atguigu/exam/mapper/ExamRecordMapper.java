package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.ExamRankingVO;
import com.atguigu.exam.vo.ExamAttemptCountVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @description 针对表【exam_record(考试记录表)】的数据库操作Mapper
 * @createDate 2025-06-20 22:37:43
 * @Entity com.atguigu.exam.entity.ExamRecord
 */
@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {
    @Select("SELECT EXISTS(SELECT 1 FROM exam_records WHERE exam_class_id = #{assignmentId})")
    boolean hasAssignmentAttempts(@Param("assignmentId") Long assignmentId);

    @Select("SELECT COUNT(*) FROM exam_records WHERE user_id = #{userId} AND exam_id = #{examId}")
    long countAttempts(@Param("userId") Long userId, @Param("examId") Integer examId);

    @Select("SELECT exam_id, COUNT(*) AS used_attempts FROM exam_records WHERE user_id = #{userId} GROUP BY exam_id")
    List<ExamAttemptCountVo> countAttemptsByPaper(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM exam_records
            WHERE user_id = #{userId} AND exam_id = #{examId} AND status = '进行中' AND is_deleted = 0
            ORDER BY id DESC LIMIT 1
            """)
    ExamRecord findActiveAttempt(@Param("userId") Long userId, @Param("examId") Integer examId);
    /**
     * 自定义查询考试排行榜
     * @param paperId 试卷ID
     * @param limit   查询条数限制
     * @return 考试排行榜信息列表
     * */
    List<ExamRankingVO> customQueryRanking(@Param("paperId") Integer paperId,
                                           @Param("limit") Integer limit,
                                           @Param("classIds") java.util.Collection<Long> classIds,
                                           @Param("userIds") java.util.Collection<Long> userIds,
                                           @Param("selfId") Long selfId);
}
