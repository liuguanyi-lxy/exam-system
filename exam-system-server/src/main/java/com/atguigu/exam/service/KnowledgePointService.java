package com.atguigu.exam.service;

import com.atguigu.exam.entity.KnowledgePoint;
import com.atguigu.exam.vo.KnowledgePointItemVo;
import com.atguigu.exam.vo.KnowledgePointSortVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 知识点管理 Service（契约 9/10 节）
 * 知识点是全局资源（不挂班级），读写均为 TEACHER / ADMIN；平铺列表 + 排序，不做树形。
 * 物理删除：删除前校验引用（存在题目标注则拒绝），删后同名重建不撞唯一键。
 */
public interface KnowledgePointService extends IService<KnowledgePoint> {

    /**
     * 知识点分页列表（管理页）：sort 升序、id 升序；questionCount 为引用该知识点的题目数
     */
    Page<KnowledgePoint> pageList(Page<KnowledgePoint> page, String name);

    /**
     * 全量列表（题目编辑页选择器）：按 sort 升序返回 {id, name, sort}，不分页
     */
    List<KnowledgePointItemVo> listAll();

    /**
     * 创建知识点：名称必填 ≤50、全局唯一（重复 → "知识点名称已存在"）；
     * sort 缺省追加到末尾（当前 max+1）；并发撞 uk_kp_name 转友好提示
     */
    KnowledgePoint createKnowledgePoint(KnowledgePoint knowledgePoint);

    /**
     * 更新知识点：存在性校验、唯一性排除自身
     */
    KnowledgePoint updateKnowledgePoint(KnowledgePoint knowledgePoint);

    /**
     * 删除知识点：不存在 → "知识点不存在"；已被题目引用 → 拒绝并提示引用数（不做级联清理）
     */
    void deleteKnowledgePoint(Long id);

    /**
     * 批量排序：列表非空；任一 id 不存在 → "知识点不存在"（整批回滚）
     */
    void sortBatch(List<KnowledgePointSortVo> items);

    /**
     * 题目知识点整体替换（契约 10.1）：不在集合中的既有标注删除、新的插入；空集合 = 清空全部；
     * 题目不存在/已逻辑删除 → "题目不存在"；任一知识点不存在 → "知识点不存在"（整批不生效）；
     * 集合与现状一致时幂等无副作用
     *
     * @param questionId 题目ID
     * @param knowledgePointIds 目标知识点集合（可空 = 清空）
     * @param operatorId 标注人 users.id（created_by 记录来源人；无登录上下文传 null）
     * @return 替换后的标注集合（按 sort 升序）
     */
    List<KnowledgePointItemVo> replaceQuestionKnowledgePoints(Long questionId, List<Long> knowledgePointIds, Long operatorId);

    /**
     * 批量取一组题目的已标注知识点（列表回显）：一次 IN 查标注表 + 一次批量查知识点，禁止循环 N+1
     *
     * @return questionId -> 标注条目列表（按知识点 sort 升序）；未标注的题目不在 Map 中
     */
    Map<Long, List<KnowledgePointItemVo>> getQuestionKnowledgePoints(Collection<Long> questionIds);

    /**
     * 单题已标注知识点（详情回显）
     */
    List<KnowledgePointItemVo> getQuestionKnowledgePoints(Long questionId);
}
