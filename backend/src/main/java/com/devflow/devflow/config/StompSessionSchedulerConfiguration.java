package com.devflow.devflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class StompSessionSchedulerConfiguration {
    @Bean
    @Primary
    public TaskScheduler stompSessionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("devflow-stomp-session-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
