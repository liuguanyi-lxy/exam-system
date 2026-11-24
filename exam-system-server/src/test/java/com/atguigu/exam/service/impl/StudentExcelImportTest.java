package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.vo.StudentImportResultVo;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StudentExcelImportTest {

    private static final long CLASS_ID = 1L;
    private final ClassServiceImpl service = new ClassServiceImpl();
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    @BeforeEach
    void setUp() {
        ClazzMapper clazzMapper = mock(ClazzMapper.class);
        when(clazzMapper.selectById(CLASS_ID)).thenReturn(new Clazz());
        ReflectionTestUtils.setField(service, "baseMapper", clazzMapper);
        ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
    }

    @ParameterizedTest
    @CsvSource({"students.XLSX,true", "students.XLS,false", "students.XlSx,true"})
    void acceptsSupportedSuffixesIgnoringCase(String filename, boolean xlsx) throws IOException {
        MockMultipartFile file = workbookFile(filename, xlsx);
        assertThat(ExcelUtil.parseStudentExcel(file)).singleElement().satisfies(row -> {
            assertThat(row.getRow()).isEqualTo(2);
            assertThat(row.getRealName()).isEqualTo("张三");
        });

        when(transactionTemplate.<StudentImportResultVo>execute(any())).thenAnswer(invocation -> {
            TransactionCallback<StudentImportResultVo> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        StudentImportResultVo result = service.importStudents(CLASS_ID, file);
        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getFailedRows()).singleElement().satisfies(row -> {
            assertThat(row.getRow()).isEqualTo(2);
            assertThat(row.getReason()).isEqualTo("用户名为空");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"students.csv", "students.XLS.exe", "students"})
    void rejectsUnsupportedSuffixEvenWithValidHssfContent(String filename) throws IOException {
        MockMultipartFile file = workbookFile(filename, false);

        assertThatIllegalArgumentException().isThrownBy(() -> ExcelUtil.parseStudentExcel(file))
                .withMessage("请上传 .xlsx 或 .xls 文件");
        assertThatIllegalArgumentException().isThrownBy(() -> service.importStudents(CLASS_ID, file))
                .withMessage("请上传 .xlsx 或 .xls 文件");
        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void rejectsExistingNonStudentAccountBeforeStartingTransaction() throws IOException {
        UserMapper userMapper = mock(UserMapper.class);
        User existingTeacher = new User();
        existingTeacher.setId(17L);
        existingTeacher.setUsername("teacher1");
        existingTeacher.setRole("TEACHER");
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(existingTeacher));
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "classMemberMapper", mock(ClassMemberMapper.class));
        ReflectionTestUtils.setField(service, "examRecordMapper", mock(ExamRecordMapper.class));

        MockMultipartFile file = validStudentWorkbook("teacher1");

        assertThatIllegalArgumentException().isThrownBy(() -> service.importStudents(CLASS_ID, file))
                .withMessageContaining("teacher1")
                .withMessageContaining("TEACHER");
        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void repeatedImportReusesExistingAccountAndSkipsDuplicateMembership() throws IOException {
        UserMapper userMapper = mock(UserMapper.class);
        ClassMemberMapper classMemberMapper = mock(ClassMemberMapper.class);
        User existingStudent = new User();
        existingStudent.setId(101L);
        existingStudent.setUsername("student1");
        existingStudent.setRole("STUDENT");
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(), java.util.List.of(existingStudent));
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        });
        when(classMemberMapper.insert(any(ClassMember.class)))
                .thenReturn(1)
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate membership"));
        when(transactionTemplate.<StudentImportResultVo>execute(any())).thenAnswer(invocation -> {
            TransactionCallback<StudentImportResultVo> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "classMemberMapper", classMemberMapper);
        ReflectionTestUtils.setField(service, "examRecordMapper", mock(ExamRecordMapper.class));

        MockMultipartFile file = validStudentWorkbook("student1");

        StudentImportResultVo first = service.importStudents(CLASS_ID, file);
        StudentImportResultVo repeated = service.importStudents(CLASS_ID, file);

        assertThat(first.getCreatedAccounts()).isEqualTo(1);
        assertThat(first.getJoinedCount()).isEqualTo(1);
        assertThat(repeated.getCreatedAccounts()).isZero();
        assertThat(repeated.getSuccessRows()).isEqualTo(1);
        assertThat(repeated.getJoinedCount()).isZero();
        verify(userMapper, times(1)).insert(any(User.class));
        verify(classMemberMapper, times(2)).insert(any(ClassMember.class));
        verify(userMapper, never()).updateById(any(User.class));
    }

    private static MockMultipartFile validStudentWorkbook(String username) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("用户名");
            header.createCell(1).setCellValue("姓名");
            Row student = sheet.createRow(1);
            student.createCell(0).setCellValue(username);
            student.createCell(1).setCellValue("教师账号");
            workbook.write(output);
            return new MockMultipartFile("file", "students.xlsx", null, output.toByteArray());
        }
    }

    private static MockMultipartFile workbookFile(String filename, boolean xlsx) throws IOException {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("用户名");
            header.createCell(1).setCellValue("姓名");
            // 缺用户名：可验证解析结果进入服务预检，而不触发用户或成员写入。
            sheet.createRow(1).createCell(1).setCellValue("张三");
            workbook.write(output);
            return new MockMultipartFile("file", filename, null, output.toByteArray());
        }
    }
}
