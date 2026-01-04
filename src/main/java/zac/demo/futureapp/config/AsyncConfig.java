package zac.demo.futureapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for async execution with custom thread pool.
 * Supports both traditional thread pool and Java 21 virtual threads.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Traditional thread pool executor for @Async annotated methods.
     * Use this for CPU-bound tasks.
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        log.info("Creating Traditional Async Executor (Thread Pool)");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(
                (r, exec) -> log.warn("Task rejected, thread pool is full and queue is full"));
        executor.initialize();

        return executor;
    }

    /**
     * Java 21 Virtual Threads executor.
     * Use this for I/O-bound tasks (HTTP calls, database queries, file I/O).
     * Virtual threads are lightweight and can scale to millions of concurrent
     * tasks.
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        log.info("Creating Java 21 Virtual Thread Executor");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Default async executor used by @Async without qualifier.
     */
    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor();
    }

    /**
     * Global exception handler for async methods.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * Custom exception handler for uncaught async exceptions.
     */
    @Slf4j
    static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Async exception in method '{}': {}", method.getName(), ex.getMessage(), ex);
        }
    }
}
