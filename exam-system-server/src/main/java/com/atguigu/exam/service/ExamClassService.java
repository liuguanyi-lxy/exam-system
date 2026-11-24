package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamClass;
import com.atguigu.exam.vo.ExamClassVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collection;
import java.util.List;

public interface ExamClassService {

    List<ExamClassVO.CreateResult> createAssignments(ExamClassVO.CreateRequest request,
                                                      Long userId,
                                                      boolean admin);

    void updateAssignment(Long id, ExamClassVO.UpdateRequest request, Long userId, boolean admin);

    void revokeAssignment(Long id, Long userId, boolean admin);

    Page<ExamClassVO.Detail> pageAssignments(Page<ExamClassVO.Detail> page,
                                              Long examId,
                                              Long classId,
                                              Long userId,
                                              boolean admin);

    ExamClassVO.Detail getAssignment(Long id, Long userId, boolean admin);

    List<ExamClassVO.StudentProgress> listStudents(Long id, Long userId, boolean admin);
}
