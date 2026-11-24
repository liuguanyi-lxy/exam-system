package com.atguigu.exam.service;

import com.atguigu.exam.vo.LearningHeartbeatResultVo;
import jakarta.servlet.http.HttpServletRequest;

public interface LearningService {
    LearningHeartbeatResultVo heartbeat(Long userId, Long chapterVideoId, String sessionId, Long sequence, Integer viewDuration,
                                        HttpServletRequest request);

    LearningHeartbeatResultVo completeExternal(Long userId, Long chapterVideoId, String sessionId);

}
