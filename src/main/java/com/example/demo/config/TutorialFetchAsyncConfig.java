package com.example.demo.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 튜토리얼 MISS 시 DART·뉴스·리서치·토론 등 외부 I/O를 병렬로 수집하기 위한 스레드 풀.
 */
@Configuration
public class TutorialFetchAsyncConfig {

    public static final String TUTORIAL_FETCH_EXECUTOR = "tutorialFetchExecutor";

    @Bean(name = TUTORIAL_FETCH_EXECUTOR)
    public Executor tutorialFetchExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("tutorial-fetch-");
        ex.initialize();
        return ex;
    }
}
