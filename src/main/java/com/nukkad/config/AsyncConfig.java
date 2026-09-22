package com.nukkad.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The rest of this app is deliberately synchronous request/response — this one executor exists solely for
 * the admin investor CSV bulk import (InvestorImportService#processImportAsync), which must not hold an HTTP
 * request thread for a 100,000-row file. A small, dedicated pool: imports are an infrequent admin action, not
 * a hot path, and a bounded queue means a burst of uploads waits rather than exhausting threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("investorImportExecutor")
    public TaskExecutor investorImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("investor-import-");
        executor.initialize();
        return executor;
    }
}
