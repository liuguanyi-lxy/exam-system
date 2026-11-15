package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.Category;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.property.Parent;
import org.redisson.connection.ServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sun.tools.attach.VirtualMachine.list;


@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper,Category> implements CategoryService {

    @Autowired
    private QuestionMapper questionMapper;
    //查询分类列表,填充每个分类的题目数量
    @Override
    public List<Category> getCategoryList() {
        List<Category> categoryList = list();
        fillCategoryCount(categoryList);
        return categoryList;
    }
    //查询分类列表,填充每个分类的题目数量,并构建子分类树形结构
    @Override
    public List<Category> getCategoryTree() {
        //查询分类表中的数据
        List<Category> categoryList = list();
        //填充每个分类的题目数量
        fillCategoryCount(categoryList);
        //根据父id进行分组，父id与其对应的子分类列表构成map
        Map<Long, List<Category>> parentIDMap = categoryList.stream().collect(Collectors.groupingBy(Category::getParentId));
        //获取所有的一级分类
        List<Category> categoryListResult = categoryList.stream().filter(category -> category.getParentId() == 0).collect(Collectors.toList());
        //对一级分类的的子分类进行填充
        categoryListResult.forEach(category->
        {
            //获取一级分类对应的子分类列表
            List<Category> childCategoryList = parentIDMap.getOrDefault(category.getId(), new ArrayList<>());
            //对一级分类下的子分类列表进行排序,升序
            childCategoryList.sort(Comparator.comparingLong(Category::getSort));
            //将排序后的子分类列表设置给一级分类
            category.setChildren(childCategoryList);
            //计算分类下子分类对应题目数量的总数
            long sum = childCategoryList.stream().mapToLong(Category::getCount).sum();
            //将父分类下的题目数量加上其子分类对应题目总数
            category.setCount(sum);
        });
        return categoryListResult;
    }
    //添加分类
    @Override
    public void addCategory(Category category) {
        //判断是否在同样的父分类下已经存在同名的分类
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId,category.getParentId());
        queryWrapper.eq(Category::getName,category.getName());
        //判断是否存在满足该条件的分类，与添加的分类的父分类一致，分类名也相同
        boolean exists = exists(queryWrapper);
        if(exists){
            Category parent = getById(category.getParentId());
            throw new RuntimeException("在%s分类下已经存在子分类%s，本次添加分类失败".formatted(parent.getName(),category.getName()));
        }
        save(category);
    }
    //更新子分类的信息
    @Override
    public void updateCategory(Category category) {
        //判断是否在同样的父分类下已经存在同名但是不同id的分类
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId,category.getParentId());
        queryWrapper.eq(Category::getName,category.getName());
        queryWrapper.ne(Category::getId,category.getId());
        boolean exists = exists(queryWrapper);
        //如果存在同名不同id的分类，则抛出异常
        if(exists){
            Category parent = getById(category.getParentId());
            throw new RuntimeException("在父分类%s下已经存在子分类%s，本次更新分类失败".formatted(parent.getName(),category.getName()));
        }
        //没有则直接更新
        updateById(category);
    }
    //根据id删除子分类
    @Override
    public void deleteCategory(Long id) {
        //判断分类是否存在
        Category category = getById(id);
        if(category == null){
            throw new RuntimeException("分类不存在");
        }
        //该分类不能是一级分类，进行判断
        if(category.getParentId()==0){
            throw new RuntimeException("该分类是一级分类，不能删除");
        }
        //该分类不能有题目
        //获得分类ID和对应题目数量的列表
        List<Map<Long, Long>> mapList = questionMapper.selectCategoryCount();
        //将mapList转换成map，key为分类id，value为题目数量
        Map<Long, Long> collect = mapList.stream()
            .collect(Collectors.toMap(
                map -> ((Number) map.get("category_id")).longValue(), 
                map -> ((Number) map.get("count")).longValue()
            ));
        //获取分类对应的题目数量
        Long count = collect.getOrDefault(id, 0L);
        log.debug("分类 {} 下的题目数量: {}", category.getName(), count);
        if(count!=0){
            throw new RuntimeException("该分类%s下有%s道题目，不能删除".formatted(category.getName(),count));
        }
        removeById(id);
    }

    //填充每个分类的题目数量
    private void fillCategoryCount(List<Category> categoryList) {
        //判断分类列表是否为空或者为null
        if(categoryList==null||categoryList.isEmpty())
        {
            throw new RuntimeException("查询的列表为空");
        }
        //查询分类对应的题目数量,每个元素是一个map，key为分类id，value为题目数量
        List<Map<Long, Long>> mapList = questionMapper.selectCategoryCount();
        //将mapList转换成map，key为分类id，value为题目数量
        Map<Long, Long> resultMap = mapList.stream()
            .collect(Collectors.toMap(
                map -> ((Number) map.get("category_id")).longValue(), 
                map -> ((Number) map.get("count")).longValue()
            ));
        //遍历分类列表，填充每个分类的题目数量
        categoryList.forEach(category ->category.setCount(resultMap.getOrDefault(category.getId(),0L)));
    }
}