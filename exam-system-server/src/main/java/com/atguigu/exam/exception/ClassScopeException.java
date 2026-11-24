package com.atguigu.exam.exception;

/**
 * 班级数据范围越权异常
 *
 * 教师访问非负责班级的单资源（详情/成员/操作）时抛出，
 * GlobalExceptionHandler 将其映射为 HTTP 403 + {code:403, message:"无权访问该班级"}。
 * 注意：不要用 RuntimeException 表达越权——兜底处理器会把它转成 code 500，丢失 403 语义。
 * 列表类接口不用本异常：教师视角列表自动过滤为负责班级子集，不报错。
 */
public class ClassScopeException extends RuntimeException {

    public ClassScopeException() {
        super("无权访问该班级");
    }

    public ClassScopeException(String message) {
        super(message);
    }
}
