package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.FavoriteService;
import com.atguigu.exam.vo.FavoriteRequestVo;
import com.atguigu.exam.vo.QuestionFavoriteVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 题目收藏控制器
 *
 * 收藏是学生个人偏好数据：路径不出现 userId，归属一律取登录上下文；
 * 契约限定仅 STUDENT 可访问。
 */
@RestController
@RequestMapping("/api/question-favorites")
@Tag(name = "题目收藏", description = "学生个人题库收藏的收藏、取消、列表与收藏状态查询")
@RequireRole({"STUDENT"})
@Slf4j
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    /**
     * 收藏题目，幂等
     */
    @PostMapping
    @Operation(summary = "收藏题目", description = "重复收藏幂等成功，不产生重复数据")
    public Result<Void> addFavorite(
            @RequestBody(required = false) FavoriteRequestVo body,
            @Parameter(hidden = true) @RequestAttribute("userId") Long userId) {
        if (body == null || body.getQuestionId() == null) {
            throw new RuntimeException("参数错误：questionId 不能为空");
        }
        favoriteService.addFavorite(userId, body.getQuestionId());
        log.info("用户{}收藏题目{}", userId, body.getQuestionId());
        return Result.<Void>success("收藏成功");
    }

    /**
     * 取消收藏，幂等
     */
    @DeleteMapping("/{questionId}")
    @Operation(summary = "取消收藏", description = "未收藏时调用同样返回成功")
    public Result<Void> removeFavorite(
            @Parameter(description = "题目ID", example = "5") @PathVariable Long questionId,
            @Parameter(hidden = true) @RequestAttribute("userId") Long userId) {
        favoriteService.removeFavorite(userId, questionId);
        log.info("用户{}取消收藏题目{}", userId, questionId);
        return Result.<Void>success("取消收藏成功");
    }

    /**
     * 收藏列表分页（按收藏时间倒序）
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询收藏列表",
            description = "按收藏时间倒序；题目信息按学生口径脱敏，不下发答案、解析与选项对错")
    public Result<Page<QuestionFavoriteVo>> list(
            @Parameter(description = "当前页码，从1开始；超过10000按10000处理", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量；超过100按100处理", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "题型筛选：CHOICE/JUDGE/TEXT/CODE，非法值忽略", example = "CHOICE")
            @RequestParam(required = false) String type,
            @Parameter(description = "题目分类ID，非数字或不存在时忽略", example = "1")
            @RequestParam(required = false) String categoryId,
            @Parameter(description = "难度筛选：EASY/MEDIUM/HARD，非法值忽略", example = "MEDIUM")
            @RequestParam(required = false) String difficulty,
            @Parameter(hidden = true) @RequestAttribute("userId") Long userId) {
        Page<QuestionFavoriteVo> result = favoriteService.pageFavorites(
                userId, page, size, type, difficulty, categoryId);
        log.info("查询收藏列表：用户{}，第{}页，每页{}条", userId, page, size);
        return Result.success(result);
    }

    /**
     * 查询某题是否已收藏（详情页收藏按钮初始状态）
     */
    @GetMapping("/{questionId}")
    @Operation(summary = "查询题目收藏状态",
            description = "题目不存在时不报错，正常返回 favorited=false")
    public Result<Map<String, Object>> getFavoriteStatus(
            @Parameter(description = "题目ID", example = "5") @PathVariable Long questionId,
            @Parameter(hidden = true) @RequestAttribute("userId") Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", questionId);
        data.put("favorited", favoriteService.isFavorited(userId, questionId));
        return Result.success(data);
    }
}
