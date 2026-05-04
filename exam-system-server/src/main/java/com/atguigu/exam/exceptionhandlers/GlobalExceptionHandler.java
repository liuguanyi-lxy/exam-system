package com.atguigu.exam.exceptionhandlers;

import com.atguigu.exam.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * projectName: day23_exam-system-server
 *
 * @author: 赵伟风
 * description: 全局异常处理器，用于统一捕获和处理系统中未处理的异常，
 * 确保所有异常都能以标准化的格式返回给前端。
 */
//我来为您详细解释这两个注解的作用和关系：
//
//## **@RestControllerAdvice 注解**
//
//**作用：** 全局异常处理器的核心注解，是 `@ControllerAdvice` 的增强版本。
//
//**特点：**
//1. **全局性**：自动扫描并应用到所有 Controller 层
//2. **组合注解**：相当于 `@ControllerAdvice` + `@ResponseBody`
//3. **功能范围**：
//   - 全局异常处理（最常用）
//   - 全局数据绑定
//   - 全局数据预处理
//
//**使用场景：**
//- 统一处理系统中未被捕获的异常
//- 确保所有异常都以相同格式返回给前端
//- 避免在每个 Controller 中重复编写异常处理代码

//## **@ExceptionHandler 注解**
//
//**作用：** 指定方法处理的异常类型，定义具体的异常处理逻辑。
//
//**特点：**
//1. **异常匹配**：通过参数指定方法要处理的异常类型
//   ```java
//   @ExceptionHandler(Exception.class)  // 捕获所有异常
//   @ExceptionHandler(NullPointerException.class)  // 只捕获空指针异常
//   ```
//
//2. **方法级别**：必须配合 `@RestControllerAdvice` 或 `@ControllerAdvice` 使用
//3. **优先级**：可以定义多个方法处理不同异常，精确匹配的优先级更高
//
//**参数说明：**
//- `value` 或默认属性：指定要处理的异常类数组
//
//---
//
//## **两者配合使用的优势**
//
//```java
//@RestControllerAdvice  // ① 声明这是一个全局异常处理器
//public class GlobalExceptionHandler {
//
//    @ExceptionHandler(Exception.class)  // ② 指定处理 Exception 类型的异常
//    public Result exception(Exception e) {
//        log.error("服务器发生运行时异常！异常信息为：{}", e.getMessage());
//        return Result.error(e.getMessage());
//    }
//}
//```
//
//
//**工作流程：**
//1. Controller 层抛出异常且未被局部捕获
//2. Spring 容器检测到 `@RestControllerAdvice` 标注的类
//3. 根据异常类型匹配对应的 `@ExceptionHandler` 方法
//4. 执行异常处理方法，返回统一的响应格式
//
//**实际应用建议：**
//```java
//@RestControllerAdvice
//public class GlobalExceptionHandler {
//
//    // 处理业务异常
//    @ExceptionHandler(BusinessException.class)
//    public Result handleBusinessException(BusinessException e) {
//        return Result.error(e.getCode(), e.getMessage());
//    }
//
//    // 处理参数校验异常
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public Result handleValidationException(MethodArgumentNotValidException e) {
//        return Result.error("参数校验失败");
//    }
//
//    // 处理其他所有异常（兜底）
//    @ExceptionHandler(Exception.class)
//    public Result handleException(Exception e) {
//        log.error("系统异常", e);
//        return Result.error("服务器内部错误");
//    }
//}
//```
//
//
//这样可以实现**分层异常处理**，不同类型的异常返回不同的提示信息，提升用户体验和系统可维护性。
@Slf4j
@RestControllerAdvice

public class GlobalExceptionHandler {

    /**
     * 处理系统运行时发生的通用异常。
     * 当Controller层抛出未被捕获的Exception时，此方法会被自动调用，
     * 记录异常日志并返回统一的错误响应。
     *
     * @param e 捕获到的异常对象，包含异常的详细信息
     * @return 包含错误信息的统一响应对象Result返回给前端，替代Controller层的响应
     */
    @ExceptionHandler(Exception.class)
    public Result exception(Exception e) {
        log.error("服务器发生运行时异常！异常信息为：{}",e.getMessage());
        return Result.error(e.getMessage());
    }
}
