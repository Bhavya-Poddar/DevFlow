package com.devflow.devflow.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SprintBurndownSchedulingConfiguration {
    @Bean
    public Clock devflowUtcClock() {
        return Clock.systemUTC();
    }
}
