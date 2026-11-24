package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.ClassService;
import com.atguigu.exam.vo.ClassmateVo;
import com.atguigu.exam.vo.MyClassVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员"我的班级"控制器（契约 8 节）
 * 归属从 token 解析（request.getAttribute("userId")），路径不出现 userId；
 * 全量不分页（决策表 #10）；同学列表不暴露 username（决策表 #11）
 */
@Slf4j
@RestController
@RequestMapping("/api/my")
@RequireRole({"STUDENT"})
@Tag(name = "我的班级", description = "学员端所属班级列表与同学列表")
public class MyClassController {

    @Autowired
    private ClassService classService;

    /**
     * 我的班级列表：所属全部班级（含已停用，由 status 区分——验收标准 4），附班级教师与学员数
     */
    @GetMapping("/classes")
    @Operation(summary = "我的班级列表", description = "返回本人所属的全部班级（含已停用），附班级教师（不下发 username）与学员数")
    public Result<List<MyClassVo>> getMyClasses(jakarta.servlet.http.HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<MyClassVo> classes = classService.listMyClasses(userId);
        log.info("学员 {} 查询我的班级：{} 个", userId, classes.size());
        return Result.success(classes);
    }

    /**
     * 我的同学列表（STUDENT-own）：本人是该班成员（roleInClass=STUDENT）才放行，否则 HTTP 403；
     * 只下发 userId/realName/joinedAt，不下发 username
     */
    @GetMapping("/classes/{classId}/classmates")
    @Operation(summary = "我的同学列表", description = "本人是该班学员才放行（越权 403）；不下发 username 等敏感信息")
    public Result<List<ClassmateVo>> getClassmates(
            @Parameter(description = "班级ID") @PathVariable Long classId,
            jakarta.servlet.http.HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<ClassmateVo> classmates = classService.listClassmates(classId, userId);
        log.info("学员 {} 查询班级 {} 同学列表：{} 人", userId, classId, classmates.size());
        return Result.success(classmates);
    }
}
