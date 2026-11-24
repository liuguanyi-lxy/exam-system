package com.atguigu.exam.service;

import com.atguigu.exam.mapper.DiagnosticQuestionChatMapper;
import com.atguigu.exam.mapper.DiagnosticReportMapper;
import com.atguigu.exam.service.QuestionChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class DiagnosticReportWorker {
    private final DiagnosticReportMapper reports;
    private final DiagnosticReportService service;
    private final DiagnosticQuestionChatMapper chats;
    private final QuestionChatService questionChats;

    @Scheduled(fixedDelayString = "${diagnostics.poll-ms:10000}", initialDelayString = "${diagnostics.poll-ms:10000}")
    public void processDue() {
        reports.missing().forEach(reports::enqueue);
        reports.due().forEach(service::generate);
        chats.due().forEach(questionChats::generate);
    }
}
