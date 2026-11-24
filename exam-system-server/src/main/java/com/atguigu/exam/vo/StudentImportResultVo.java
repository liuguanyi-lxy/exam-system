package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 学员 Excel 批量导入结果 Vo（契约 5.3）
 * 处理规则：行级失败不中断整批计入 failedRows；文件级错误（空文件/格式不符/超 500 行）整单拒绝；
 * 入库阶段整体单事务，撞 uk_class_user_role 转"已在班"幂等成功
 */
@Data
@Schema(description = "学员批量导入结果")
public class StudentImportResultVo {

    @Schema(description = "Excel 数据行数（不含表头）", example = "3")
    private Integer totalRows;

    @Schema(description = "成功行数（含幂等跳过行）", example = "3")
    private Integer successRows;

    @Schema(description = "本次新建账号数", example = "2")
    private Integer createdAccounts;

    @Schema(description = "本次实际新增入班人次（已在班跳过不计）", example = "3")
    private Integer joinedCount;

    @Schema(description = "失败行列表")
    private List<FailedRow> failedRows = new ArrayList<>();

    /**
     * 行级失败明细：row 为 Excel 行号，reason 为用户可读原因
     */
    @Data
    @Schema(description = "导入失败行")
    public static class FailedRow {

        @Schema(description = "Excel 行号", example = "3")
        private Integer row;

        @Schema(description = "用户名", example = "stu01")
        private String username;

        @Schema(description = "失败原因", example = "用户名为空")
        private String reason;

        public FailedRow() {
        }

        public FailedRow(Integer row, String username, String reason) {
            this.row = row;
            this.username = username;
            this.reason = reason;
        }
    }
}
