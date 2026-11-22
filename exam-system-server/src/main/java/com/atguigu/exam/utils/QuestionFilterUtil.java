package com.atguigu.exam.utils;

import java.util.Set;

/**
 * 错题本 / 收藏列表筛选参数归一化工具。
 *
 * 契约约定：type / difficulty 传枚举外的值、categoryId 非数字或不存在时，
 * 忽略该筛选条件而不是报错（与 /api/exam-records/list 对非法 status 的处理一致）。
 */
public final class QuestionFilterUtil {

    /** 题库实际存在的题型（QuestionType 枚举缺少 CODE，不能直接用枚举校验） */
    private static final Set<String> VALID_TYPES = Set.of("CHOICE", "JUDGE", "TEXT", "CODE");

    private static final Set<String> VALID_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private QuestionFilterUtil() {
    }

    /** 合法题型原样返回，其余返回 null（忽略筛选） */
    public static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String trimmed = type.trim();
        return VALID_TYPES.contains(trimmed) ? trimmed : null;
    }

    /** 合法难度原样返回，其余返回 null（忽略筛选） */
    public static String normalizeDifficulty(String difficulty) {
        if (difficulty == null) {
            return null;
        }
        String trimmed = difficulty.trim();
        return VALID_DIFFICULTIES.contains(trimmed) ? trimmed : null;
    }

    /** 非数字或空串返回 null；数字合法与否由调用方再校验分类是否存在 */
    public static Long parseCategoryId(String categoryId) {
        if (categoryId == null) {
            return null;
        }
        String trimmed = categoryId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
