package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.Video;

final class LearningVideoRules {
    static final int EXTERNAL_SECONDS = 60;
    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String COMPLETED = "COMPLETED";

    private LearningVideoRules() {
    }

    static int ratio(ChapterVideo link) {
        return link.getCompletionRatio() == null ? 70 : link.getCompletionRatio();
    }

    static boolean isExternal(Video video) {
        return Integer.valueOf(Video.SOURCE_TYPE_EXTERNAL).equals(video.getSourceType());
    }

    static int requiredSeconds(ChapterVideo link, Video video) {
        if (isExternal(video)) return EXTERNAL_SECONDS;
        if (video.getDuration() == null || video.getDuration() <= 0) {
            throw new IllegalArgumentException("该视频缺少有效时长");
        }
        return (int) ((video.getDuration() * (long) ratio(link) + 99) / 100);
    }
}
