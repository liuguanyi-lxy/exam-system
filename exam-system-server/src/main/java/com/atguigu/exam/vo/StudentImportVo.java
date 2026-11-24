package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 学员 Excel 导入行 Vo
 * Excel 格式：列 A=用户名（必填）、列 B=姓名（选填，缺省用用户名）；第 1 行表头，数据行从第 2 行起。
 * row 记录 Excel 行号（1 起，含表头），行级失败时回填到导入结果的 failedRows
 */
@Data
@Schema(description = "学员导入行")
public class StudentImportVo {

    @Schema(description = "Excel 行号（含表头计数）", example = "2")
    private int row;

    @Schema(description = "用户名（必填）", example = "student1")
    private String username;

    @Schema(description = "姓名（选填，缺省用用户名）", example = "张三")
    private String realName;
}
