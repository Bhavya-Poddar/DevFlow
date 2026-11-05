package com.devflow.devflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.devflow.devflow.security.ProjectPrivacyInterceptor;

@Configuration
public class ProjectPrivacyWebConfig implements WebMvcConfigurer {
    private final ProjectPrivacyInterceptor interceptor;

    public ProjectPrivacyWebConfig(ProjectPrivacyInterceptor interceptor) { this.interceptor = interceptor; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
