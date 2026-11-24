package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.KnowledgePoint;
import com.atguigu.exam.service.KnowledgePointService;
import com.atguigu.exam.vo.KnowledgePointItemVo;
import com.atguigu.exam.vo.KnowledgePointSortVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识点控制器（契约 9 节：分页列表/全量列表/创建/更新/删除/批量排序）
 * 知识点是全局资源（不挂班级），读写均为 TEACHER / ADMIN；学生端 M1 不提供知识点接口
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge-points")
@RequireRole({"TEACHER", "ADMIN"})
@Tag(name = "知识点管理", description = "知识点 CRUD、排序与全量列表（题目标注与 M4 诊断的数据基础）")
public class KnowledgePointController {

    private static final int MAX_PAGE = 10_000;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private KnowledgePointService knowledgePointService;

    /**
     * 知识点分页列表（管理页）：sort 升序、id 升序；附 questionCount 引用计数
     */
    @GetMapping("/list")
    @Operation(summary = "知识点分页列表", description = "支持名称模糊筛选；按 sort 升序排列；questionCount 为引用该知识点的题目数")
    public Result<Page<KnowledgePoint>> getKnowledgePointList(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "名称模糊筛选") @RequestParam(required = false) String name) {
        page = normalizePage(page);
        size = normalizePageSize(size);
        Page<KnowledgePoint> result = knowledgePointService.pageList(new Page<>(page, size), name);
        log.info("查询知识点分页列表：page={} size={} total={}", page, size, result.getTotal());
        return Result.success(result);
    }

    private int normalizePage(Integer page) {
        return page == null ? 1 : Math.min(MAX_PAGE, Math.max(1, page));
    }

    private int normalizePageSize(Integer size) {
        return size == null ? 10 : Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    /**
     * 知识点全量列表（题目编辑页选择器）：不分页，按 sort 升序返回 {id, name, sort}
     */
    @GetMapping("/all")
    @Operation(summary = "知识点全量列表", description = "题目编辑页多选选择器数据源，不分页")
    public Result<List<KnowledgePointItemVo>> getAllKnowledgePoints() {
        return Result.success(knowledgePointService.listAll());
    }

    /**
     * 创建知识点：名称全局唯一；sort 缺省追加到末尾
     */
    @PostMapping
    @Operation(summary = "创建知识点", description = "名称必填且全局唯一；sort 缺省取当前 max+1")
    public Result<KnowledgePoint> createKnowledgePoint(@RequestBody KnowledgePoint knowledgePoint) {
        KnowledgePoint created = knowledgePointService.createKnowledgePoint(knowledgePoint);
        log.info("创建知识点成功：id={} name={}", created.getId(), created.getName());
        return Result.success(created, "创建成功");
    }

    /**
     * 更新知识点：唯一性排除自身
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新知识点", description = "名称唯一性排除自身；目标不存在返回业务失败")
    public Result<KnowledgePoint> updateKnowledgePoint(
            @Parameter(description = "知识点ID") @PathVariable Long id,
            @RequestBody KnowledgePoint knowledgePoint) {
        knowledgePoint.setId(id);
        KnowledgePoint updated = knowledgePointService.updateKnowledgePoint(knowledgePoint);
        log.info("更新知识点成功：id={} name={}", id, updated.getName());
        return Result.success(updated);
    }

    /**
     * 删除知识点：被题目引用时拒绝并提示引用数
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识点", description = "已被题目引用时拒绝删除并提示引用数；不做级联清理")
    public Result<Void> deleteKnowledgePoint(@Parameter(description = "知识点ID") @PathVariable Long id) {
        knowledgePointService.deleteKnowledgePoint(id);
        log.info("删除知识点成功：id={}", id);
        return Result.success(null, "删除成功");
    }

    /**
     * 批量排序：管理页拖拽排序后一次性提交；任一 id 不存在整批回滚
     */
    @PostMapping("/sort")
    @Operation(summary = "知识点批量排序", description = "整批提交；任一 id 不存在整批回滚")
    public Result<Void> sortKnowledgePoints(@RequestBody List<KnowledgePointSortVo> items) {
        knowledgePointService.sortBatch(items);
        return Result.success(null, "排序已保存");
    }
}
