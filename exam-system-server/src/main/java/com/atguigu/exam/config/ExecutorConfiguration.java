package com.atguigu.exam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局共享线程池
 * 用于：AI判卷并行调用、热门题目访问计数等异步任务
 * 统一使用共享线程池，避免到处 new Thread；CallerRunsPolicy在任务打满时退化为调用者执行，保证任务不丢
 */
@Configuration
public class ExecutorConfiguration {

    @Bean("examExecutor")
    public ThreadPoolExecutor examExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "exam-worker-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
        return new ThreadPoolExecutor(
                4, 8, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
