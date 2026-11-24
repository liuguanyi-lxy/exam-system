package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 同学条目 Vo —— 学员端同学列表（不暴露敏感信息）
 * 只下发 userId/realName/joinedAt，不下发 username（登录名半敏感，决策表 #11）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "同学信息（不含登录名）")
public class ClassmateVo {

    @Schema(description = "同学用户ID", example = "21")
    private Long userId;

    @Schema(description = "姓名", example = "张三")
    private String realName;

    @Schema(description = "入班时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date joinedAt;
}
