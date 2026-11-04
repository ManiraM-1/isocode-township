package com.isocode.township.ai;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class ClassificationExecutorConfiguration {

    public static final String EXECUTOR = "classificationExecutor";
    private static final Logger log = LoggerFactory.getLogger(ClassificationExecutorConfiguration.class);
    private static final int WORKERS = 2;
    private static final int MAX_WORKERS = 4;
    private static final int QUEUE_CAPACITY = 200;

    @Bean(name = EXECUTOR)
    public Executor classificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("classify-");
        executor.setCorePoolSize(WORKERS);
        executor.setMaxPoolSize(MAX_WORKERS);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Classification queue is full; the complaint keeps its keyword result until the sweep"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
