package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.KnowledgePoint;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionKnowledgePoint;
import com.atguigu.exam.mapper.KnowledgePointMapper;
import com.atguigu.exam.mapper.QuestionKnowledgePointMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.KnowledgePointService;
import com.atguigu.exam.vo.KnowledgePointItemVo;
import com.atguigu.exam.vo.KnowledgePointSortVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识点管理 Service 实现
 *
 * 全部 LambdaQueryWrapper（M1 零 XML）；删除/标注均物理删行。
 * 题目标注整体替换：删旧插新在同一事务内（@Transactional），
 * 撞 uk_qkp_question_kp 的并发场景由唯一索引 + 幂等语义兜底。
 */
@Slf4j
@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint>
        implements KnowledgePointService {

    private static final int MAX_NAME_LENGTH = 50;

    @Autowired
    private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Autowired
    private QuestionMapper questionMapper;

    // ==================== 知识点 CRUD ====================

    @Override
    public Page<KnowledgePoint> pageList(Page<KnowledgePoint> page, String name) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(name != null && !name.isBlank(), KnowledgePoint::getName, name == null ? null : name.trim());
        // 排序：sort 升序、id 升序（契约 9.1，与选择器顺序一致）
        wrapper.orderByAsc(KnowledgePoint::getSort).orderByAsc(KnowledgePoint::getId);
        page(page, wrapper);
        fillQuestionCounts(page.getRecords());
        return page;
    }

    @Override
    public List<KnowledgePointItemVo> listAll() {
        return list(new LambdaQueryWrapper<KnowledgePoint>()
                        .orderByAsc(KnowledgePoint::getSort).orderByAsc(KnowledgePoint::getId))
                .stream().map(kp -> new KnowledgePointItemVo(kp.getId(), kp.getName(), kp.getSort()))
                .toList();
    }

    @Override
    public KnowledgePoint createKnowledgePoint(KnowledgePoint knowledgePoint) {
        String name = requireValidName(knowledgePoint.getName());
        checkNameUnique(name, null);
        knowledgePoint.setName(name);
        if (knowledgePoint.getDescription() != null) {
            knowledgePoint.setDescription(knowledgePoint.getDescription().trim());
        }
        // sort 缺省追加到末尾（当前 max+1）
        if (knowledgePoint.getSort() == null) {
            knowledgePoint.setSort(nextSort());
        }
        try {
            save(knowledgePoint);
        } catch (DuplicateKeyException e) {
            // 并发撞 uk_kp_name → 友好提示（设计文档第 7 节边界语义）
            throw new RuntimeException("知识点名称已存在");
        }
        KnowledgePoint created = getById(knowledgePoint.getId());
        created.setQuestionCount(0);
        return created;
    }

    @Override
    public KnowledgePoint updateKnowledgePoint(KnowledgePoint knowledgePoint) {
        KnowledgePoint exist = getById(knowledgePoint.getId());
        if (exist == null) {
            throw new RuntimeException("知识点不存在");
        }
        String name = requireValidName(knowledgePoint.getName());
        checkNameUnique(name, knowledgePoint.getId());
        knowledgePoint.setName(name);
        if (knowledgePoint.getDescription() != null) {
            knowledgePoint.setDescription(knowledgePoint.getDescription().trim());
        }
        // sort 未传则保持原值（updateById 默认忽略 null 字段）
        try {
            updateById(knowledgePoint);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("知识点名称已存在");
        }
        KnowledgePoint updated = getById(knowledgePoint.getId());
        updated.setQuestionCount(countQuestionReferences(knowledgePoint.getId()));
        return updated;
    }

    @Override
    public void deleteKnowledgePoint(Long id) {
        if (getById(id) == null) {
            throw new RuntimeException("知识点不存在");
        }
        int references = countQuestionReferences(id);
        if (references > 0) {
            // 被题目引用 → 拒绝删除并提示引用数（决策表 #13，不做级联清理）
            throw new RuntimeException("该知识点已被 " + references + " 道题引用，请先移除标注");
        }
        removeById(id); // 物理删行（实体不继承 BaseEntity，无 @TableLogic）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortBatch(List<KnowledgePointSortVo> items) {
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("排序列表不能为空");
        }
        for (KnowledgePointSortVo item : items) {
            if (item == null || item.getId() == null || item.getSort() == null) {
                throw new IllegalArgumentException("排序条目必须包含知识点 ID 和排序值");
            }
        }
        // 任一 id 不存在 → 整批回滚（契约 9.5）
        List<Long> ids = items.stream().map(KnowledgePointSortVo::getId).toList();
        Map<Long, KnowledgePoint> existMap = listByIds(ids).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, Function.identity(), (a, b) -> a));
        for (Long id : ids) {
            if (!existMap.containsKey(id)) {
                throw new RuntimeException("知识点不存在");
            }
        }
        for (KnowledgePointSortVo item : items) {
            KnowledgePoint kp = new KnowledgePoint();
            kp.setId(item.getId());
            kp.setSort(item.getSort());
            updateById(kp);
        }
        log.info("知识点批量排序完成：{} 项", items.size());
    }

    // ==================== 题目-知识点标注 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<KnowledgePointItemVo> replaceQuestionKnowledgePoints(Long questionId, List<Long> knowledgePointIds,
                                                                     Long operatorId) {
        // 题目存在性校验：Question 继承 BaseEntity，@TableLogic 自动过滤已逻辑删除行
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new RuntimeException("题目不存在");
        }
        // 去重保序（null 元素剔除）；null 请求体视为清空全部标注（契约 10.1：空数组 = 清空）
        List<Long> distinctIds = knowledgePointIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(knowledgePointIds.stream()
                        .filter(java.util.Objects::nonNull).toList()));
        // 任一知识点不存在 → 整批不生效（事务回滚）
        Map<Long, KnowledgePoint> kpMap = distinctIds.isEmpty() ? Map.of()
                : listByIds(distinctIds).stream()
                        .collect(Collectors.toMap(KnowledgePoint::getId, Function.identity(), (a, b) -> a));
        for (Long id : distinctIds) {
            if (!kpMap.containsKey(id)) {
                throw new RuntimeException("知识点不存在");
            }
        }
        // 整体替换：先删旧行，再插新行；集合与现状一致时结果幂等（删除+重插同内容）
        questionKnowledgePointMapper.delete(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                .eq(QuestionKnowledgePoint::getQuestionId, questionId));
        for (Long id : distinctIds) {
            QuestionKnowledgePoint link = new QuestionKnowledgePoint();
            link.setQuestionId(questionId);
            link.setKnowledgePointId(id);
            link.setCreatedBy(operatorId);
            questionKnowledgePointMapper.insert(link);
        }
        log.info("题目标注知识点替换完成：questionId={} 共 {} 个标注，操作人 {}", questionId, distinctIds.size(), operatorId);
        return distinctIds.stream()
                .map(kpMap::get)
                .sorted(Comparator.comparing(KnowledgePoint::getSort,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(KnowledgePoint::getId))
                .map(kp -> new KnowledgePointItemVo(kp.getId(), kp.getName(), kp.getSort()))
                .toList();
    }

    @Override
    public Map<Long, List<KnowledgePointItemVo>> getQuestionKnowledgePoints(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        // 一次 IN 查标注表（走 uk_qkp_question_kp 前缀）
        List<QuestionKnowledgePoint> links = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .in(QuestionKnowledgePoint::getQuestionId, questionIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        // 一次批量查知识点名称（两步批量查，禁止循环 N+1）
        Map<Long, KnowledgePoint> kpMap = listByIds(
                        links.stream().map(QuestionKnowledgePoint::getKnowledgePointId).distinct().toList())
                .stream().collect(Collectors.toMap(KnowledgePoint::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<KnowledgePointItemVo>> result = new HashMap<>();
        for (QuestionKnowledgePoint link : links) {
            KnowledgePoint kp = kpMap.get(link.getKnowledgePointId());
            if (kp == null) {
                continue; // 删题/删点残留行的防御性跳过
            }
            result.computeIfAbsent(link.getQuestionId(), k -> new ArrayList<>())
                    .add(new KnowledgePointItemVo(kp.getId(), kp.getName(), kp.getSort()));
        }
        // 每题内按知识点 sort 升序、id 升序（与选择器顺序一致）
        result.values().forEach(list -> list.sort(Comparator
                .comparing(KnowledgePointItemVo::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(KnowledgePointItemVo::getId)));
        return result;
    }

    @Override
    public List<KnowledgePointItemVo> getQuestionKnowledgePoints(Long questionId) {
        return getQuestionKnowledgePoints(Collections.singletonList(questionId))
                .getOrDefault(questionId, List.of());
    }

    // ==================== 私有工具 ====================

    /** 名称必填且 ≤50 字符（契约 9.3） */
    private String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("知识点名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new RuntimeException("知识点名称不能超过 50 个字符");
        }
        return trimmed;
    }

    /** 名称全局唯一（excludeId 排除自身）；重复 → "知识点名称已存在" */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getName, name);
        wrapper.ne(excludeId != null, KnowledgePoint::getId, excludeId);
        if (count(wrapper) > 0) {
            throw new RuntimeException("知识点名称已存在");
        }
    }

    /** 下一个排序号：当前 max+1（无记录从 1 起） */
    private int nextSort() {
        KnowledgePoint maxSortRow = getOne(new LambdaQueryWrapper<KnowledgePoint>()
                .orderByDesc(KnowledgePoint::getSort).last("LIMIT 1"), false);
        return maxSortRow == null || maxSortRow.getSort() == null ? 1 : maxSortRow.getSort() + 1;
    }

    /** 引用该知识点的题目数（question_knowledge_points 有记录即引用） */
    private int countQuestionReferences(Long knowledgePointId) {
        Long count = questionKnowledgePointMapper.selectCount(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                .eq(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointId));
        return count == null ? 0 : count.intValue();
    }

    /** 管理页分页列表的引用计数：一次 IN 查询分组计数，防 N+1 */
    private void fillQuestionCounts(List<KnowledgePoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<Long> ids = points.stream().map(KnowledgePoint::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (QuestionKnowledgePoint link : questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .in(QuestionKnowledgePoint::getKnowledgePointId, ids))) {
            counts.merge(link.getKnowledgePointId(), 1L, Long::sum);
        }
        for (KnowledgePoint kp : points) {
            kp.setQuestionCount(counts.getOrDefault(kp.getId(), 0L).intValue());
        }
    }
}
