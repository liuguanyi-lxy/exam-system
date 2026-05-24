package com.atguigu.exam.service.impl;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.atguigu.exam.config.properties.KimiProperties;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.service.KimiAiService;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.GradingResult;
import com.atguigu.exam.vo.QuestionImportVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Kimi AI服务实现类
 * 调用Kimi API智能生成题目
 */
@Slf4j
@Service
public class KimiAiServiceImpl implements KimiAiService {

    @Autowired
    private WebClient webClient;

    @Autowired
    private KimiProperties kimiProperties;
    //构建生成ai评语，根据总分、最大分、题目数量、正确数量构建的提示词构建答题结果评价
    @Override
    public String buildSummary(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount) throws InterruptedException {
        //1. 构建提示词
        String summaryPrompt  = buildSummaryPrompt(totalScore,maxScore,questionCount,correctCount);
        //2. 调用kimiai
        String summary = callKimiAi(summaryPrompt);
        //3. 结果解析
        return summary;
    }
    //使用ai,进行简答题判断，返回判题结果
    @Override
    public GradingResult gradingTextQuestion(Question question, String userAnswer, Integer maxScore) throws InterruptedException {
        //1.生成ai调用的提示词
        String gradingPrompt = buildGradingPrompt(question, userAnswer, maxScore);
        //2.调用ai模型，获取返回结果
        String content = callKimiAi(gradingPrompt);
        //3.进行结果的解析 -》GradingResult
        /*
            prompt.append("{\n");
            prompt.append("  \"score\": 实际得分(整数),\n");
            prompt.append("  \"feedback\": \"具体的评价反馈(50字以内)\",\n");
            prompt.append("  \"reason\": \"扣分原因或得分依据(30字以内)\"\n");
            prompt.append("}");
         */
        com.alibaba.fastjson2.JSONObject jsonObject = JSON.parseObject(content);
        Integer aiScore = jsonObject.getInteger("score");
        String feedback = jsonObject.getString("feedback");
        String reason = jsonObject.getString("reason");
        if (aiScore > maxScore) aiScore = maxScore;
        if (aiScore < 0) aiScore = 0;
        return  new GradingResult(aiScore, feedback, reason);
    }

    /**
     * 根据前端传递的想要生成的题目的要求对象，生成对应的提示词
     * @param request
     * @return
     */
    @Override
    public String buildPrompt(AiGenerateRequestVo request) {
        //1. 构建提示词
        StringBuilder prompt = new StringBuilder();

        prompt.append("请为我生成").append(request.getCount()).append("道关于【")
                .append(request.getTopic()).append("】的题目。\n\n");

        prompt.append("要求：\n");

        // 题目类型要求
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            // 将请求中的题目类型字符串按逗号分割变成数组，然后转换为List集合
            List<String> typeList = Arrays.asList(request.getTypes().split(","));
            // 向提示信息中添加题目类型部分的开头
            prompt.append("- 包含的题目类型：");
            // 遍历类型列表，为每种类型添加对应的中文描述
            for (String type : typeList) {
                // 去除类型字符串两端的空格
                switch (type.trim()) {
                    case "CHOICE":
                        // 添加选择题类型描述
                        prompt.append("选择题");
                        // 如果请求中包含多选选项且为true，则添加说明
                        if (request.getIncludeMultiple() != null && request.getIncludeMultiple()) {
                            prompt.append("(包含单选和多选)");
                        }
                        // 添加空格分隔不同类型
                        prompt.append(" ");
                        break;
                    case "JUDGE":
                        // 添加判断题类型描述，并强调答案平衡的重要性
                        prompt.append("判断题（**重要：确保正确答案和错误答案的数量大致平衡，不要全部都是正确或错误**） ");
                        break;
                    case "TEXT":
                        // 添加简答题类型描述
                        prompt.append("简答题 ");
                        break;
                }
            }
            prompt.append("\n");
        }

        // 难度要求
        if (request.getDifficulty() != null) {
            String difficultyText = switch (request.getDifficulty()) {
                case "EASY" -> "简单";
                case "MEDIUM" -> "中等";
                case "HARD" -> "困难";
                default -> "中等";
            };
            prompt.append("- 难度等级：").append(difficultyText).append("\n");
        }

        // 额外要求
        if (request.getRequirements() != null && !request.getRequirements().isEmpty()) {
            prompt.append("- 特殊要求：").append(request.getRequirements()).append("\n");
        }

        // 判断题特别要求
        if (request.getTypes() != null && request.getTypes().contains("JUDGE")) {
            prompt.append("- **判断题特别要求**：\n");
            prompt.append("  * 确保生成的判断题中，正确答案(TRUE)和错误答案(FALSE)的数量尽量平衡\n");
            prompt.append("  * 不要所有判断题都是正确的或都是错误的\n");
            prompt.append("  * 错误的陈述应该是常见的误解或容易混淆的概念\n");
            prompt.append("  * 正确的陈述应该是重要的基础知识点\n");
        }
        // 生成JSON格式
        prompt.append("\n请严格按照以下JSON格式返回，不要包含任何其他文字：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"title\": \"题目内容\",\n");
        prompt.append("      \"type\": \"CHOICE|JUDGE|TEXT\",\n");
        prompt.append("      \"multi\": true/false,\n");
        prompt.append("      \"difficulty\": \"EASY|MEDIUM|HARD\",\n");
        prompt.append("      \"score\": 5,\n");
        prompt.append("      \"choices\": [\n");
        prompt.append("        {\"content\": \"选项内容\", \"isCorrect\": true/false, \"sort\": 1}\n");
        prompt.append("      ],\n");
        prompt.append("      \"answer\": \"TRUE或FALSE(判断题专用)|文本答案(简答题专用)|A、B、C、D(选择题答案)\",\n");
        prompt.append("      \"analysis\": \"题目解析\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("注意：\n");
        prompt.append("1. 选择题必须有choices数组，判断题和简答题设置answer字段\n");
        prompt.append("2. 多选题的multi字段设为true，单选题设为false\n");
        prompt.append("3. **判断题的answer字段只能是\"TRUE\"或\"FALSE\"，请确保答案分布合理**\n");
        prompt.append("4. 每道题都要有详细的解析\n");
        prompt.append("5. 题目要有实际价值，贴近实际应用场景\n");
        prompt.append("6. 严格按照JSON格式返回，确保可以正确解析\n");

        // 如果只生成判断题，额外强调答案平衡
        if (request.getTypes() != null && request.getTypes().equals("JUDGE") && request.getCount() > 1) {
            prompt.append("7. **判断题答案分布要求**：在").append(request.getCount()).append("道判断题中，");
            int halfCount = request.getCount() / 2;
            if (request.getCount() % 2 == 0) {
                prompt.append("请生成").append(halfCount).append("道正确(TRUE)和").append(halfCount).append("道错误(FALSE)的题目");
            } else {
                prompt.append("请生成约").append(halfCount).append("-").append(halfCount + 1).append("道正确(TRUE)和约").append(halfCount).append("-").append(halfCount + 1).append("道错误(FALSE)的题目");
            }
        }

        return prompt.toString();
    }


    /**
     * 
     * 进行失败重试 给3次机会！！！
     *    kimi失败场景
     *      假失败 -》 调用成功 1. 结果格式不对  2. 速率限制 -> try
     *        |
     *      真失败 -》 抛出异常 -> catch
     * 封装调用kimi模型，最终返结果
     * @param prompt
     * @return 返回生成题目json 结果 / choices / message / content
     */
    @Override
    /**
     * 调用Kimi AI接口的方法
     * 该方法包含重试机制，最多重试3次，用于处理网络波动或API限流等临时性问题
     *
     * @param prompt 输入的提示词，将作为用户消息发送给Kimi AI模型
     * @return AI返回的内容字符串，从响应结构的choices[0].message.content中提取
     * @throws InterruptedException 线程中断异常，在重试间隔期间可能被中断
     * @throws RuntimeException 当重试3次后仍然失败，或返回内容为空时抛出运行时异常
     */
    public String callKimiAi(String prompt) throws InterruptedException {

        // 最多重试3次的配置常量
        int maxTry = 3;
        // 循环执行请求，支持失败重试机制
        for (int i = 1; i <= 3; i++) {
            try {
                // 构建符合Kimi API规范的请求体结构
                // 参考文档：https://platform.moonshot.cn/docs/api/chat#%E8%AF%B7%E6%B1%82%E5%86%85%E5%AE%B9
                
                // 创建用户消息对象，设置角色和提示词内容
                Map<String,String> userMap = new HashMap<>();
                userMap.put("role","user");
                userMap.put("content",prompt);
                List<Map> messagesList = new ArrayList<>();
                messagesList.add(userMap);

                // 组装完整的请求体，包含模型配置、消息列表和生成参数
                Map<String,Object> requestBody = new HashMap<>();
                requestBody.put("model",kimiProperties.getModel());
                requestBody.put("messages",messagesList);
                requestBody.put("temperature", kimiProperties.getTemperature());
                requestBody.put("max_tokens", kimiProperties.getMaxTokens());

                // 发起POST请求调用Kimi API，设置超时时间为100秒
                // 使用WebClient发送HTTP POST请求并获取响应结果
                Mono<String> stringMono = webClient.post()           // 创建POST请求
                        .bodyValue(requestBody)                      // 设置请求体内容
                        .retrieve()                                 // 执行请求并获取响应
                        .bodyToMono(String.class)                   // 将响应体转换为Mono<String>
                        .timeout(Duration.ofSeconds(100));           // 设置超时时间为100秒

                // 阻塞等待异步响应结果，将响应转换为同步处理方式
                String result = stringMono.block();
                // 解析JSON格式的响应数据
                JSONObject resultJsonObject = JSONObject.parseObject(result);

                // 检查响应中是否包含错误信息，如有错误则抛出异常
                // 参考文档：https://platform.moonshot.cn/docs/api/chat#错误说明
                if (resultJsonObject.containsKey("error")){
                    throw new RuntimeException("访问错误了，错误信息为:" +
                            resultJsonObject.getJSONObject("error").getString("message") );
                }
                // 从响应结构中提取AI生成的内容
                // 参考文档：https://platform.moonshot.cn/docs/api/chat#%E8%BF%94%E5%9B%9E%E5%86%85%E5%AE%B9
                // 响应结构：choices[0].message.content
                String content = resultJsonObject.getJSONArray("choices").getJSONObject(0).
                        getJSONObject("message").getString("content");
                log.debug("调用kimi返回的结果为：{}",content);

                // 验证返回内容是否为空，确保有效性
                if (content == null || content.isEmpty()){
                    throw new RuntimeException("调用成功！但是没有返回结果！！");
                }
                return content;
            }catch (Exception e){
                // 记录当前尝试失败的次数
                log.debug("第{}次尝试调用失败了！",i);
                // 失败后等待1秒再进行重试，避免频繁请求
                Thread.sleep(1000);
                // 判断是否已达到最大重试次数，如达到则抛出异常
                if(i == maxTry){
                    e.printStackTrace();
                    throw new RuntimeException("已经重试3次！依然失败！请稍后再试！！");
                }
            }
        }
        throw new RuntimeException("已经重试3次！依然失败！请稍后再试！！");
    }



    /**
     * ai题目信息生成
     * @param request
     * @return
     */
    @Override
    /**
     * AI生成题目方法
     * @param request AI智能生成题目所需要的前端传入请求参数
     * @return 生成的题目列表
     * @throws InterruptedException 线程中断异常
     */
    public List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request) throws InterruptedException {
        // 步骤1：执行参数校验工作（当前代码中未实现具体校验逻辑）
        
        // 步骤2：根据AI生成题目的要求构建提示词
        String prompt = buildPrompt(request);
        
        // 步骤3：根据提示词调用Kimi AI接口获取生成的题目内容
        String content = callKimiAi(prompt);
        
        // 步骤4：解析AI返回的JSON格式结果
        // AI返回的数据格式示例：
        // ```json
        //    {
        //       "questions": [{}, {}, {}]
        //    }
        // ```
        
        // 查找JSON代码块的起始位置标记
        int startIndex = content.indexOf("```json");
        // 查找JSON代码块的结束位置标记
        int endIndex = content.lastIndexOf("```");
        
        // 验证起始和结束标记都存在，且位置关系正确
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            // 提取真正的JSON字符串内容（跳过"```json"这7个字符）
            String realResult = content.substring(startIndex + 7, endIndex);
            
            // 打印提取的JSON内容用于调试
            System.out.println("realResult = " + realResult);
            
            // 将JSON字符串解析为JSONObject对象
            JSONObject jsonObject = JSONObject.parseObject(realResult);
            
            // 从JSON对象中获取key为questions的value数组
            JSONArray questions = jsonObject.getJSONArray("questions");
            
            // 创建用于存储转换后题目对象的列表
            List<QuestionImportVo> questionImportVoList = new ArrayList<>();
            
            // 遍历所有题目数据
            for (int i = 0; i < questions.size(); i++) {
                // 获取第i个题目的JSON对象
                JSONObject questionJson = questions.getJSONObject(i);
                
                // 创建题目导入VO对象
                QuestionImportVo questionImportVo = new QuestionImportVo();
                
                // 设置题目标题
                questionImportVo.setTitle(questionJson.getString("title"));
                
                // 设置题目类型（CHOICE/JUDGE/TEXT）
                questionImportVo.setType(questionJson.getString("type"));
                
                // 设置是否为多选题
                questionImportVo.setMulti(questionJson.getBoolean("multi"));
                
                // 设置题目难度（EASY/MEDIUM/HARD）
                questionImportVo.setDifficulty(questionJson.getString("difficulty"));
                
                // 设置题目分值
                questionImportVo.setScore(questionJson.getInteger("score"));
                
                // 设置题目解析说明
                questionImportVo.setAnalysis(questionJson.getString("analysis"));
                
                // 设置题目分类ID（从请求参数中获取）
                questionImportVo.setCategoryId(request.getCategoryId());

                // 如果是选择题类型，需要处理选项数据
                if ("CHOICE".equals(questionImportVo.getType())) {
                    // 获取选择题的选项数组
                    JSONArray choices = questionJson.getJSONArray("choices");
                    
                    // 创建选项列表，初始化容量为选项数量
                    List<QuestionImportVo.ChoiceImportDto> choiceImportDtoList = new ArrayList<>(choices.size());
                    
                    // 遍历题目的所有选项
                    for (int i1 = 0; i1 < choices.size(); i1++) {
                        // 获取第i1个选项的JSON对象
                        JSONObject choicesJSONObject = choices.getJSONObject(i1);
                        
                        // 创建选项导入DTO对象
                        QuestionImportVo.ChoiceImportDto choiceImportDto = new QuestionImportVo.ChoiceImportDto();
                        
                        // 设置选项内容
                        choiceImportDto.setContent(choicesJSONObject.getString("content"));
                        
                        // 设置该选项是否为正确答案
                        choiceImportDto.setIsCorrect(choicesJSONObject.getBoolean("isCorrect"));
                        
                        // 设置选项排序号
                        choiceImportDto.setSort(choicesJSONObject.getInteger("sort"));
                        
                        // 将选项添加到选项列表
                        choiceImportDtoList.add(choiceImportDto);
                    }
                    
                    // 将选项列表设置到题目对象中
                    questionImportVo.setChoices(choiceImportDtoList);
                }
                
                // 设置题目答案（判断题为TRUE/FALSE，简答题为文本答案）
                questionImportVo.setAnswer(questionJson.getString("answer"));
                
                // 将处理完成的题目对象添加到列表中
                questionImportVoList.add(questionImportVo);
            }
            
            // 返回所有生成的题目列表
            return questionImportVoList;
        }
        
        // 如果JSON格式解析失败，抛出运行时异常
        throw new RuntimeException("ai生成题目json数据结构错误，无法正常解析！数据为：%s".formatted(content));
    }


    /**
     * 根据传入的题目，学生答题答案和题目满分构建ai判卷提示词
     */
    /**
     * 构建判卷提示信息
     * @param question 题目信息对象
     * @param userAnswer 学生答案
     * @param maxScore 题目满分
     * @return 返回格式化的判卷提示字符串
     */
    private String buildGradingPrompt(Question question, String userAnswer, Integer maxScore) {
        StringBuilder prompt = new StringBuilder();
        // 添加判卷老师角色说明
        prompt.append("你是一名专业的考试阅卷老师，请对以下题目进行判卷：\n\n");

        // 添加题目信息部分
        prompt.append("【题目信息】\n");
        prompt.append("题型：").append(getQuestionTypeText(question.getType())).append("\n");
        prompt.append("题目：").append(question.getTitle()).append("\n");
        prompt.append("标准答案：").append(question.getAnswer().getAnswer()).append("\n");
        prompt.append("满分：").append(maxScore).append("分\n\n");

        // 添加学生答案部分，处理空答案情况
        prompt.append("【学生答案】\n");
        prompt.append(userAnswer.trim().isEmpty() ? "（未作答）" : userAnswer).append("\n\n");

        // 添加判卷要求部分
        prompt.append("【判卷要求】\n");
        // 客观题判卷标准
        if ("CHOICE".equals(question.getType()) || "JUDGE".equals(question.getType())) {
            prompt.append("- 客观题：答案完全正确得满分，答案错误得0分\n");
        } else if ("TEXT".equals(question.getType())) {
            prompt.append("- 主观题：根据答案的准确性、完整性、逻辑性进行评分\n");
            prompt.append("- 答案要点正确且完整：80-100%分数\n");
            prompt.append("- 答案基本正确但不够完整：60-80%分数\n");
            prompt.append("- 答案部分正确：30-60%分数\n");
            prompt.append("- 答案完全错误或未作答：0分\n");
        }

        prompt.append("\n请按以下JSON格式返回判卷结果：\n");
        prompt.append("{\n");
        prompt.append("  \"score\": 实际得分(整数),\n");
        prompt.append("  \"feedback\": \"具体的评价反馈(50字以内)\",\n");
        prompt.append("  \"reason\": \"扣分原因或得分依据(30字以内)\"\n");
        prompt.append("}");

        return prompt.toString();
    }


    /**
     * 根据传入的题目的英文类型获取题目的中文类型文本
     */
    private String getQuestionTypeText(String type) {
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("CHOICE", "选择题");
        typeMap.put("JUDGE", "判断题");
        typeMap.put("TEXT", "简答题");
        return typeMap.getOrDefault(type, "未知题型");
    }


    /**
     * 构建考试总评提示词
     */
    private String buildSummaryPrompt(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount) {
        // 计算百分比：将总分转换为double类型后除以最大分数，再乘以100得到百分比
        double percentage = (double) totalScore / maxScore * 100;

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名资深的IT行业教育专家，请为学生的考试表现提供专业的总评和学习建议：\n\n");

        prompt.append("【考试成绩】\n");
        prompt.append("总得分：").append(totalScore).append("/").append(maxScore).append("分\n");
        prompt.append("得分率：").append(String.format("%.1f", percentage)).append("%\n");
        prompt.append("题目总数：").append(questionCount).append("道\n");
        prompt.append("答对题数：").append(correctCount).append("道\n\n");

        prompt.append("【要求】\n");
        prompt.append("请提供一份150字左右的考试总评，包括：\n");
        prompt.append("1. 对本次考试表现的客观评价\n");
        prompt.append("2. 指出优势和不足之处\n");
        prompt.append("3. 提供具体的学习建议和改进方向\n");
        prompt.append("4. 给予鼓励和激励\n\n");

        prompt.append("请直接返回总评内容，无需特殊格式：");

        return prompt.toString();
    }
}