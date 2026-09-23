package com.nukkad.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The rest of this app is deliberately synchronous request/response — these executors exist solely for the
 * admin bulk-import pipelines (InvestorImportService#processImportAsync, GrantImportService#startImport),
 * which must not hold an HTTP request thread for the duration of a file import. Small, dedicated pools:
 * imports are an infrequent admin action, not a hot path, and a bounded queue means a burst of uploads waits
 * rather than exhausting threads.
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

    @Bean("grantImportExecutor")
    public TaskExecutor grantImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("grant-import-");
        executor.initialize();
        return executor;
    }
}
