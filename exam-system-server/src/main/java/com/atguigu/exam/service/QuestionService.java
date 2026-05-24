package com.atguigu.exam.service;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.QuestionImportVo;
import com.atguigu.exam.vo.QuestionPageVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 题目业务服务接口 - 定义题目相关的业务逻辑
 * 
 * Spring Boot三层架构教学要点：
 * 1. Service层：业务逻辑层，位于Controller和Mapper之间
 * 2. 接口设计：定义业务方法规范，便于不同实现类的切换
 * 3. 继承IService：使用MyBatis Plus提供的通用服务接口，减少重复代码
 * 4. 事务管理：Service层是事务的边界，复杂业务操作应该加@Transactional
 * 5. 业务封装：将复杂的数据操作封装成有业务意义的方法
 * 
 * MyBatis Plus教学：
 * - IService<T>：提供基础的CRUD方法（save、update、remove、list等）
 * - 自定义方法：在接口中定义特定业务需求的方法
 * - 实现类：继承ServiceImpl<Mapper, Entity>并实现自定义业务方法
 * 
 * 设计原则：
 * - 单一职责：专门处理题目相关的业务逻辑
 * - 开闭原则：通过接口定义，便于扩展新的实现
 * - 依赖倒置：Controller依赖接口而不是具体实现
 * 
 * @author 智能学习平台开发团队
 * @version 1.0
 */
public interface QuestionService extends IService<Question> {

    /**
     * 自定义分页查询题目
     * @param questionPage 分页对象
     * @param questionPageVo 查询条件
     */
    void customPageQuery(Page<Question> questionPage, QuestionPageVo questionPageVo);
    // 自定义根据ID详情查询题目
    Question customDetailQuestion(Long id);
    // 自定义保存题目信息
    void customSaveQuestion(Question question);
    // 自定义更新题目信息
    void customUpdateQuestion(Question question);
    // 自定义根据ID删除题目
    void customRemoveQuestionById(Long id);

    List<Question> customFindPopularQuestions(Integer size);
    //将传入的文件转换成对应的JAVA对象
    List<QuestionImportVo> preViewExcel(MultipartFile file) throws IOException;
    //将传入文件进行解析成JAVA对象并且保存题目列表
    String importExeclBatchQuestions(MultipartFile file) throws IOException;
    //批量导入题目业务,根据传入的题目导入列表进行题目保存
    int importBatchQuestions(List<QuestionImportVo> questions);
}