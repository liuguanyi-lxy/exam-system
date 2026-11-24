package com.atguigu.exam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record QuestionChatRequest(@NotNull UUID requestId, @NotBlank @Size(max = 2000) String message) {}
