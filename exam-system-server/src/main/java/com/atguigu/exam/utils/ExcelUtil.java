package com.atguigu.exam.utils;

import com.atguigu.exam.vo.QuestionImportVo;
import com.atguigu.exam.vo.StudentImportVo;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Excel文件处理工具类
 * 用于解析题目导入的Excel文件
 */
public class ExcelUtil {
    
    /**
     * 解析Excel文件并转换为题目导入DTO列表
     * 
     * Excel格式要求：
     * 第一行为标题行：题目内容|题目类型|是否多选|分类ID|难度|分值|选项A|选项B|选项C|选项D|正确答案|解析
     * 
     * @param file Excel文件
     * @return 题目导入DTO列表
     * @throws IOException 文件读取异常
     */
    public static List<QuestionImportVo> parseExcel(MultipartFile file) throws IOException {
        List<QuestionImportVo> questions = new ArrayList<>();
        
        // 获取文件输入流
        InputStream inputStream = file.getInputStream();
        Workbook workbook = null;
        
        try {
            // 根据文件扩展名选择对应的工作簿类型
            String fileName = file.getOriginalFilename();
            if (fileName != null && fileName.endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream); // Excel 2007+
            } else {
                workbook = new HSSFWorkbook(inputStream); // Excel 97-2003
            }
            
            // 获取第一个工作表
            Sheet sheet = workbook.getSheetAt(0);
            
            // 从第二行开始读取数据（第一行是标题）
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                QuestionImportVo question = new QuestionImportVo();
                
                // 读取基本信息
                question.setTitle(getCellValue(row.getCell(0))); // 题目内容
                question.setType(getCellValue(row.getCell(1))); // 题目类型
                question.setMulti("是".equals(getCellValue(row.getCell(2))) || "true".equalsIgnoreCase(getCellValue(row.getCell(2)))); // 是否多选
                
                // 分类ID
                String categoryIdStr = getCellValue(row.getCell(3));
                if (categoryIdStr != null && !categoryIdStr.isEmpty()) {
                    try {
                        question.setCategoryId(Long.parseLong(categoryIdStr));
                    } catch (NumberFormatException e) {
                        question.setCategoryId(1L); // 默认分类
                    }
                }
                
                question.setDifficulty(getCellValue(row.getCell(4))); // 难度
                
                // 分值
                String scoreStr = getCellValue(row.getCell(5));
                if (scoreStr != null && !scoreStr.isEmpty()) {
                    try {
                        question.setScore(Integer.parseInt(scoreStr));
                    } catch (NumberFormatException e) {
                        question.setScore(5); // 默认分值
                    }
                }
                
                // 处理选择题选项
                if ("CHOICE".equals(question.getType())) {
                    List<QuestionImportVo.ChoiceImportDto> choices = new ArrayList<>();
                    String correctAnswer = getCellValue(row.getCell(10)); // 正确答案列
                    
                    // 读取选项A-D
                    for (int j = 0; j < 4; j++) {
                        String optionContent = getCellValue(row.getCell(6 + j));
                        if (optionContent != null && !optionContent.trim().isEmpty()) {
                            QuestionImportVo.ChoiceImportDto choice = new QuestionImportVo.ChoiceImportDto();
                            choice.setContent(optionContent);
                            choice.setSort(j + 1);
                            
                            // 判断是否为正确答案
                            char optionLabel = (char) ('A' + j);
                            boolean isCorrect = correctAnswer != null && correctAnswer.contains(String.valueOf(optionLabel));
                            choice.setIsCorrect(isCorrect);
                            
                            choices.add(choice);
                        }
                    }
                    question.setChoices(choices);
                } else {
                    // 判断题和简答题直接设置答案
                    question.setAnswer(getCellValue(row.getCell(10)));
                }
                
                question.setAnalysis(getCellValue(row.getCell(11))); // 解析
                
                // 验证必填字段
                if (question.getTitle() != null && !question.getTitle().trim().isEmpty() &&
                    question.getType() != null && !question.getType().trim().isEmpty()) {
                    questions.add(question);
                }
            }
            
        } finally {
            if (workbook != null) {
                workbook.close();
            }
            inputStream.close();
        }
        
        return questions;
    }
    
    /**
     * 获取单元格的字符串值
     * @param cell 单元格
     * @return 字符串值
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // 如果是数字，转换为字符串
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    /**
     * 生成Excel模板文件的字节数组
     * @return Excel模板文件的字节数组
     */
    public static byte[] generateTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("题目导入模板");
        
        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "题目内容", "题目类型", "是否多选", "分类ID", "难度", "分值",
            "选项A", "选项B", "选项C", "选项D", "正确答案", "解析"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
        
        // 创建示例数据行
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue("以下哪个是Spring框架的核心特性？");
        exampleRow.createCell(1).setCellValue("CHOICE");
        exampleRow.createCell(2).setCellValue("否");
        exampleRow.createCell(3).setCellValue("1");
        exampleRow.createCell(4).setCellValue("MEDIUM");
        exampleRow.createCell(5).setCellValue("5");
        exampleRow.createCell(6).setCellValue("依赖注入");
        exampleRow.createCell(7).setCellValue("面向切面编程");
        exampleRow.createCell(8).setCellValue("事务管理");
        exampleRow.createCell(9).setCellValue("以上都是");
        exampleRow.createCell(10).setCellValue("D");
        exampleRow.createCell(11).setCellValue("Spring框架的核心特性包括依赖注入、面向切面编程和事务管理等。");
        
        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        }
    }

    /**
     * 生成学员批量导入模板文件的字节数组（契约 5.4）
     * 两列表头：用户名、姓名；列 A=用户名（必填），列 B=姓名（选填）
     * @return Excel 模板文件字节
     */
    public static byte[] generateStudentTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("学员导入模板");

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {"用户名", "姓名"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // 创建示例数据行
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue("student01");
        exampleRow.createCell(1).setCellValue("张三");

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        }
    }

    /**
     * 解析学员导入 Excel 文件（契约 5.3，解析阶段不入库）
     * 第 1 行表头，数据行从第 2 行起；null 行跳过（不计入结果），
     * 用户名为空的行也返回（row 已记录），由导入服务计入 failedRows，不在这里整单拒绝。
     *
     * @param file Excel 文件（.xlsx/.xls，后缀不区分大小写）
     * @return 学员导入行列表（含 Excel 行号）
     * @throws IllegalArgumentException 文件为空或后缀不受支持
     * @throws IOException 文件读取异常
     */
    public static List<StudentImportVo> parseStudentExcel(MultipartFile file) throws IOException {
        String fileName = file == null || file.isEmpty() ? null : file.getOriginalFilename();
        String normalizedName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        boolean xlsx = normalizedName.endsWith(".xlsx");
        if (!xlsx && !normalizedName.endsWith(".xls")) {
            throw new IllegalArgumentException("请上传 .xlsx 或 .xls 文件");
        }

        List<StudentImportVo> rows = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = xlsx ? new XSSFWorkbook(inputStream) : new HSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                StudentImportVo vo = new StudentImportVo();
                vo.setRow(i + 1); // Excel 行号（1 起，含表头），failedRows 回显用
                vo.setUsername(getCellValue(row.getCell(0)));
                vo.setRealName(getCellValue(row.getCell(1)));
                rows.add(vo);
            }
        }

        return rows;
    }
}