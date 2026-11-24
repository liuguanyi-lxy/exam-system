package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.DiagnosticReportService;
import com.atguigu.exam.service.QuestionChatService;
import com.atguigu.exam.vo.DiagnosticReport;
import com.atguigu.exam.vo.QuestionChatRequest;
import com.atguigu.exam.vo.QuestionChatRound;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/diagnostics/{recordId}")
@RequireRole({"ADMIN", "TEACHER", "STUDENT"})
@RequiredArgsConstructor
public class DiagnosticReportController {
    private final DiagnosticReportService reports;
    private final QuestionChatService chats;

    public record ReviewRequest(String note) {}

    @GetMapping
    public Result<DiagnosticReport.View> get(@PathVariable int recordId, @RequestAttribute("userId") Long userId,
                                              @RequestAttribute("userRole") String role) {
        return Result.success(reports.get(recordId, userId, role));
    }

    @PostMapping("/retry")
    public Result<DiagnosticReport.View> retry(@PathVariable int recordId, @RequestAttribute("userId") Long userId,
                                               @RequestAttribute("userRole") String role) {
        return Result.success(reports.retry(recordId, userId, role));
    }

    @PostMapping("/review")
    @RequireRole({"ADMIN","TEACHER"})
    public Result<DiagnosticReport.View> review(@PathVariable int recordId, @RequestAttribute("userId") Long userId,
                                                @RequestAttribute("userRole") String role, @RequestBody ReviewRequest review) {
        return Result.success(reports.review(recordId, userId, role, review.note()));
    }

    @GetMapping("/questions/{questionId}/chats")
    public Result<List<QuestionChatRound>> chats(@PathVariable int recordId, @PathVariable long questionId,
                                                 @RequestAttribute("userId") Long userId,
                                                 @RequestAttribute("userRole") String role) {
        return Result.success(chats.list(recordId, questionId, userId, role));
    }

    @PostMapping("/questions/{questionId}/chats")
    public Result<QuestionChatRound> ask(@PathVariable int recordId, @PathVariable long questionId,
                                         @RequestAttribute("userId") Long userId,
                                         @RequestAttribute("userRole") String role,
                                         @Validated @RequestBody QuestionChatRequest request) {
        return Result.success(chats.ask(recordId, questionId, userId, role, request));
    }
}
