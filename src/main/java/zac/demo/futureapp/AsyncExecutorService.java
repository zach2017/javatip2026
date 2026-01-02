package zac.demo.futureapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A generic async executor service that wraps CompletableFuture.
 * Can be used to execute any function asynchronously.
 * Supports both traditional thread pools and Java 21 virtual threads.
 */
@Slf4j
@Service
public class AsyncExecutorService {

    private final Executor platformExecutor;
    private final Executor virtualExecutor;

    public AsyncExecutorService(
            @Qualifier("asyncExecutor") Executor platformExecutor,
            @Qualifier("virtualThreadExecutor") Executor virtualExecutor) {
        this.platformExecutor = platformExecutor;
        this.virtualExecutor = virtualExecutor;
    }

    /**
     * Execute a Supplier asynchronously using platform threads.
     * Best for CPU-bound tasks.
     *
     * @param supplier The supplier function to execute
     * @param <T>      The return type
     * @return CompletableFuture containing the result
     */
    public <T> CompletableFuture<T> executeAsync(Supplier<T> supplier) {
        log.info("Submitting async task with return value (platform thread)");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting async execution on thread: {}", Thread.currentThread());
            try {
                T result = supplier.get();
                log.info("Async execution completed successfully");
                return result;
            } catch (Exception e) {
                log.error("Async execution failed: {}", e.getMessage(), e);
                throw e;
            }
        }, platformExecutor);
    }

    /**
     * Execute a Supplier asynchronously using Java 21 virtual threads.
     * Best for I/O-bound tasks (HTTP calls, database queries, file I/O).
     *
     * @param supplier The supplier function to execute
     * @param <T>      The return type
     * @return CompletableFuture containing the result
     */
    public <T> CompletableFuture<T> executeAsyncVirtual(Supplier<T> supplier) {
        log.info("Submitting async task with return value (virtual thread)");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting async execution on virtual thread: {}", Thread.currentThread());
            try {
                T result = supplier.get();
                log.info("Async execution completed successfully on virtual thread");
                return result;
            } catch (Exception e) {
                log.error("Async execution failed on virtual thread: {}", e.getMessage(), e);
                throw e;
            }
        }, virtualExecutor);
    }

    /**
     * Execute a Runnable asynchronously.
     * Use this when you don't need to return a value.
     *
     * @param runnable The runnable to execute
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> executeAsync(Runnable runnable) {
        log.info("Submitting async task (no return value)");
        return CompletableFuture.runAsync(() -> {
            log.info("Starting async execution on thread: {}", Thread.currentThread().getName());
            try {
                runnable.run();
                log.info("Async execution completed successfully");
            } catch (Exception e) {
                log.error("Async execution failed: {}", e.getMessage(), e);
                throw e;
            }
        }, platformExecutor);
    }

    /**
     * Execute a Runnable asynchronously using virtual threads.
     *
     * @param runnable The runnable to execute
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> executeAsyncVirtual(Runnable runnable) {
        log.info("Submitting async task (no return value, virtual thread)");
        return CompletableFuture.runAsync(() -> {
            log.info("Starting async execution on virtual thread: {}", Thread.currentThread());
            try {
                runnable.run();
                log.info("Async execution completed successfully on virtual thread");
            } catch (Exception e) {
                log.error("Async execution failed on virtual thread: {}", e.getMessage(), e);
                throw e;
            }
        }, virtualExecutor);
    }

    /**
     * Execute a Function asynchronously with an input parameter.
     * Use this when you need to transform input to output.
     *
     * @param function The function to execute
     * @param input    The input parameter
     * @param <T>      The input type
     * @param <R>      The return type
     * @return CompletableFuture containing the result
     */
    public <T, R> CompletableFuture<R> executeAsync(Function<T, R> function, T input) {
        log.info("Submitting async task with input parameter");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting async execution on thread: {}", Thread.currentThread().getName());
            try {
                R result = function.apply(input);
                log.info("Async execution completed successfully");
                return result;
            } catch (Exception e) {
                log.error("Async execution failed: {}", e.getMessage(), e);
                throw e;
            }
        }, platformExecutor);
    }

    /**
     * Execute a Consumer asynchronously with an input parameter.
     * Use this when you need to consume input but don't return a value.
     *
     * @param consumer The consumer to execute
     * @param input    The input parameter
     * @param <T>      The input type
     * @return CompletableFuture<Void>
     */
    public <T> CompletableFuture<Void> executeAsync(Consumer<T> consumer, T input) {
        log.info("Submitting async consumer task");
        return CompletableFuture.runAsync(() -> {
            log.info("Starting async execution on thread: {}", Thread.currentThread().getName());
            try {
                consumer.accept(input);
                log.info("Async execution completed successfully");
            } catch (Exception e) {
                log.error("Async execution failed: {}", e.getMessage(), e);
                throw e;
            }
        }, platformExecutor);
    }

    /**
     * Execute multiple suppliers in parallel and combine results.
     *
     * @param suppliers Array of suppliers to execute
     * @param <T>       The return type
     * @return CompletableFuture containing array of results
     */
    @SafeVarargs
    public final <T> CompletableFuture<T[]> executeAllAsync(Supplier<T>... suppliers) {
        log.info("Submitting {} parallel async tasks", suppliers.length);

        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futures = new CompletableFuture[suppliers.length];

        for (int i = 0; i < suppliers.length; i++) {
            futures[i] = executeAsync(suppliers[i]);
        }

        return CompletableFuture.allOf(futures)
                .thenApply(v -> {
                    @SuppressWarnings("unchecked")
                    T[] results = (T[]) new Object[futures.length];
                    for (int i = 0; i < futures.length; i++) {
                        results[i] = futures[i].join();
                    }
                    log.info("All {} parallel tasks completed", futures.length);
                    return results;
                });
    }

    /**
     * Execute multiple suppliers in parallel using virtual threads.
     * Ideal for many I/O-bound parallel tasks.
     *
     * @param suppliers Array of suppliers to execute
     * @param <T>       The return type
     * @return CompletableFuture containing array of results
     */
    @SafeVarargs
    public final <T> CompletableFuture<T[]> executeAllAsyncVirtual(Supplier<T>... suppliers) {
        log.info("Submitting {} parallel async tasks (virtual threads)", suppliers.length);

        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futures = new CompletableFuture[suppliers.length];

        for (int i = 0; i < suppliers.length; i++) {
            futures[i] = executeAsyncVirtual(suppliers[i]);
        }

        return CompletableFuture.allOf(futures)
                .thenApply(v -> {
                    @SuppressWarnings("unchecked")
                    T[] results = (T[]) new Object[futures.length];
                    for (int i = 0; i < futures.length; i++) {
                        results[i] = futures[i].join();
                    }
                    log.info("All {} parallel virtual thread tasks completed", futures.length);
                    return results;
                });
    }

    /**
     * Chain multiple async operations.
     *
     * @param supplier Initial supplier
     * @param function Function to apply to the result
     * @param <T>      Initial type
     * @param <R>      Result type
     * @return CompletableFuture with chained result
     */
    public <T, R> CompletableFuture<R> executeAndThen(Supplier<T> supplier, Function<T, R> function) {
        log.info("Submitting chained async task");
        return executeAsync(supplier)
                .thenApplyAsync(result -> {
                    log.info("Executing chained function on thread: {}", Thread.currentThread().getName());
                    return function.apply(result);
                }, platformExecutor);
    }

    /**
     * Execute with timeout using Java 21 features.
     *
     * @param supplier The supplier to execute
     * @param timeout  Timeout duration
     * @param <T>      The return type
     * @return CompletableFuture with timeout
     */
    public <T> CompletableFuture<T> executeWithTimeout(Supplier<T> supplier, Duration timeout) {
        log.info("Submitting async task with timeout: {}", timeout);
        return executeAsync(supplier)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Execute with default value on timeout.
     *
     * @param supplier     The supplier to execute
     * @param timeout      Timeout duration
     * @param defaultValue Default value if timeout occurs
     * @param <T>          The return type
     * @return CompletableFuture with timeout fallback
     */
    public <T> CompletableFuture<T> executeWithTimeoutDefault(Supplier<T> supplier, Duration timeout, T defaultValue) {
        log.info("Submitting async task with timeout and default: {}", timeout);
        return executeAsync(supplier)
                .completeOnTimeout(defaultValue, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Execute any of multiple suppliers - returns first completed result.
     *
     * @param suppliers Array of suppliers
     * @param <T>       The return type
     * @return CompletableFuture with first completed result
     */
    @SafeVarargs
    public final <T> CompletableFuture<T> executeAnyAsync(Supplier<T>... suppliers) {
        log.info("Submitting {} tasks, returning first completed", suppliers.length);

        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futures = new CompletableFuture[suppliers.length];

        for (int i = 0; i < suppliers.length; i++) {
            futures[i] = executeAsync(suppliers[i]);
        }

        return CompletableFuture.anyOf(futures)
                .thenApply(result -> {
                    log.info("First task completed with result");
                    @SuppressWarnings("unchecked")
                    T typedResult = (T) result;
                    return typedResult;
                });
    }

    /**
     * Execute with exception handling.
     *
     * @param supplier The supplier to execute
     * @param fallback Fallback function on exception
     * @param <T>      The return type
     * @return CompletableFuture with exception handling
     */
    public <T> CompletableFuture<T> executeWithFallback(Supplier<T> supplier, Function<Throwable, T> fallback) {
        log.info("Submitting async task with fallback");
        return executeAsync(supplier)
                .exceptionally(ex -> {
                    log.warn("Task failed, using fallback: {}", ex.getMessage());
                    return fallback.apply(ex);
                });
    }
}
