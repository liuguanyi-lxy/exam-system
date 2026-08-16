package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.KimiAiService;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.QuestionImportVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目批量管理控制器 - 处理题目批量操作相关的HTTP请求
 * 包括Excel导入、AI生成题目、批量验证等功能
 */
@Slf4j  // 日志注解
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/questions/batch")  // 题目批量操作API路径前缀
@CrossOrigin(origins = "*")  // 允许跨域访问
@Tag(name = "题目批量操作", description = "题目批量管理相关操作，包括Excel导入、AI生成题目、批量验证等功能")  // Swagger API分组
public class QuestionBatchController {
    
    @Autowired
    private QuestionService questionService;
    @Autowired
    private KimiAiService kimiAiService;
    /**
     * 下载Excel导入模板
     * @return Excel模板文件
     */
    @GetMapping("/template")  // 处理GET请求
    @Operation(summary = "下载Excel导入模板", description = "下载题目批量导入的Excel模板文件")  // API描述
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
/**
 * 生成Excel模板并封装为响应实体
 * 主要功能：
 * 1. 调用ExcelUtil生成模板字节数组
 * 2. 构建响应实体，设置响应头和内容类型
 * 3. 返回可下载的Excel模板文件
 */
        //获取下载模板的字节数组
        //调用ExcelUtil工具类的generateTemplate方法生成Excel模板文件
        byte[] template = ExcelUtil.generateTemplate();
        //封装ResponseEntity，用于构建HTTP响应
        ResponseEntity<byte[]> responseEntity = ResponseEntity.ok()
        //设置Content-Disposition响应头，指定文件名为question_template.xlsx
                .header("Content-Disposition", "attachment; filename=question_template.xlsx")
        //设置Content-Type为application/octet-stream，表示这是一个二进制文件
                .contentType(MediaType.APPLICATION_OCTET_STREAM)//二进制文件，不确定类型
        //设置响应体内容为生成的模板字节数组
                .body(template);
        return responseEntity;
    }
    
    /**
     * 预览Excel文件内容（不入库）
     * @param file Excel文件
     * @return 解析出的题目列表
     */
    @PostMapping("/preview-excel")  // 处理POST请求
    @Operation(summary = "预览Excel文件内容", description = "解析并预览Excel文件中的题目内容，不会导入到数据库")  // API描述
    public Result<List<QuestionImportVo>> previewExcel(
            @Parameter(description = "Excel文件，支持.xls和.xlsx格式") @RequestParam("file") MultipartFile file) throws IOException {
        List<QuestionImportVo> questionImportVoList = questionService.preViewExcel(file);
        log.info("预览解析execl接口调用成功！题目数量：{},数据为：{}",questionImportVoList.size(),questionImportVoList);
        return Result.success(questionImportVoList);
    }
    
    /**
     * 从Excel文件批量导入题目
     * @param file Excel文件
     * @return 导入结果
     */
    @PostMapping("/import-excel")  // 处理POST请求
    @Operation(summary = "从Excel文件批量导入题目", description = "解析Excel文件并将题目批量导入到数据库")  // API描述
    public Result<String> importFromExcel(
            @Parameter(description = "Excel文件，包含题目数据") @RequestParam("file") MultipartFile file) throws IOException {
            String result =  questionService.importExeclBatchQuestions(file);
            log.info(result);
            return Result.success(result);
    }
    
    /**
     * 使用AI生成题目（预览，不入库）
     * @param request AI生成请求参数
     * @return 生成的题目列表
     */
    @PostMapping("/ai-generate")  // 处理POST请求
    @Operation(summary = "AI智能生成题目", description = "使用AI技术根据指定主题和要求智能生成题目，支持预览后再决定是否导入")  // API描述
    public Result<List<QuestionImportVo>> generateQuestionsByAi(
            //// 当请求到达时，Spring会自动验证request对象,根据AiGenerateRequestVo类的属性上的要求进行验证
            @RequestBody @Validated AiGenerateRequestVo request) throws InterruptedException {
        List<QuestionImportVo> questionImportVos = kimiAiService.aiGenerateQuestions(request);
        log.info("AI生成标题为{}的题目成功，计划生成{}道题目，实际生成{}道题目",request.getTopic(),request.getCount(),questionImportVos.size());
        return Result.success(questionImportVos);
    }
    
    /**
     * 批量导入题目（通用接口，支持Excel导入或AI生成后的确认导入）
     * @param questions 题目导入DTO列表
     * @return 导入结果
     */
    @PostMapping("/import-questions")  // 处理POST请求
    @Operation(summary = "批量导入题目", description = "将题目列表批量导入到数据库，支持Excel解析后的导入或AI生成后的确认导入")  // API描述
    public Result<String> importQuestions(@RequestBody List<QuestionImportVo> questions) {
        int successCount =  questionService.importBatchQuestions(questions);
        log.info("批量导入题目接口调用成功！ 一共需要导入{}道题目，成功导入了：{}道题！" ,questions.size(),successCount);
        return Result.success("批量导入题目接口调用成功！ 一共：%s 题目需要导入，成功导入了：%s 道题！".formatted(questions.size(),successCount));
    }
    
    /**
     * 验证题目数据
     * @param questions 题目列表
     * @return 验证结果
     */
    @PostMapping("/validate")  // 处理POST请求
    @Operation(summary = "验证题目数据", description = "验证题目数据的完整性和格式正确性，返回验证结果和错误信息")  // API描述
    public Result<String> validateQuestions(@RequestBody List<QuestionImportVo> questions) {
        if (questions == null || questions.isEmpty()) {
            return Result.success("没有需要验证的题目");
        }
        // 逐题验证并收集错误信息
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String error = validateSingleQuestion(questions.get(i), i + 1);
            if (error != null) {
                errors.add(error);
            }
        }
        if (errors.isEmpty()) {
            return Result.success("全部 " + questions.size() + " 道题目验证通过");
        }
        log.warn("题目数据验证未通过，共 {} 个错误", errors.size());
        return Result.error("验证未通过，共 " + errors.size() + " 个错误：\n" + String.join("\n", errors));
    }
    
    /**
     * 验证单个题目数据
     * @param question 题目数据
     * @param index 题目序号
     * @return 错误信息，如果为null表示验证通过
     */
    private String validateSingleQuestion(QuestionImportVo question, int index) {
        // 验证基本字段
        if (question.getTitle() == null || question.getTitle().trim().isEmpty()) {
            return String.format("第%d题：题目内容不能为空", index);
        }
        
        if (question.getType() == null || question.getType().trim().isEmpty()) {
            return String.format("第%d题：题目类型不能为空", index);
        }
        
        if (!"CHOICE".equals(question.getType()) && !"JUDGE".equals(question.getType()) && !"TEXT".equals(question.getType())) {
            return String.format("第%d题：题目类型必须是CHOICE、JUDGE或TEXT", index);
        }
        
        // 验证选择题特有字段
        if ("CHOICE".equals(question.getType())) {
            if (question.getChoices() == null || question.getChoices().isEmpty()) {
                return String.format("第%d题：选择题必须有选项", index);
            }
            
            if (question.getChoices().size() < 2) {
                return String.format("第%d题：选择题至少需要2个选项", index);
            }
            
            boolean hasCorrectAnswer = question.getChoices().stream()
                    .anyMatch(choice -> choice.getIsCorrect() != null && choice.getIsCorrect());
            
            if (!hasCorrectAnswer) {
                return String.format("第%d题：选择题必须有正确答案", index);
            }
        } else {
            // 判断题和简答题需要答案
            if (question.getAnswer() == null || question.getAnswer().trim().isEmpty()) {
                return String.format("第%d题：%s必须有答案", index, 
                    "JUDGE".equals(question.getType()) ? "判断题" : "简答题");
            }
        }
        
        return null; // 验证通过
    }
} 