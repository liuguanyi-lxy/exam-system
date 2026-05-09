package com.atguigu.exam.service;

import com.atguigu.exam.entity.Category;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface CategoryService extends IService<Category> {


      List<Category> getCategoryList();

      List<Category> getCategoryTree();

      void addCategory(Category category);

      void updateCategory(Category category);

      void deleteCategory(Long id);
} 