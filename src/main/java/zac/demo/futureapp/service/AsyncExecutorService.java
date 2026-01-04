package zac.demo.futureapp.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic async executor wrapper around CompletableFuture.
 *
 * Provides:
 * - platform thread pool execution
 * - Java 21 virtual thread per task execution (good for I/O style work)
 * - common patterns like "all", "any", chaining, timeout, fallback
 */
@Slf4j
@Service
public class AsyncExecutorService {

    private final ExecutorService platformExecutor;
    private final ExecutorService virtualExecutor;

    public AsyncExecutorService() {
        // A bounded pool for CPU-ish work; tune as needed.
        this.platformExecutor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()));
        // Virtual threads for I/O-heavy tasks.
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // ------------------------
    // Core execute methods
    // ------------------------

    public <T> CompletableFuture<T> executeAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, platformExecutor);
    }

    public <T> CompletableFuture<T> executeAsyncVirtual(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, virtualExecutor);
    }

    public CompletableFuture<Void> executeAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, platformExecutor);
    }

    public <T, R> CompletableFuture<R> executeAsync(Function<T, R> function, T input) {
        return CompletableFuture.supplyAsync(() -> function.apply(input), platformExecutor);
    }

    public <T> CompletableFuture<Void> executeAsync(Consumer<T> consumer, T input) {
        return CompletableFuture.runAsync(() -> consumer.accept(input), platformExecutor);
    }

    // ------------------------
    // Combinators
    // ------------------------

    @SafeVarargs
    public final <T> CompletableFuture<List<T>> executeAllAsync(Supplier<T>... suppliers) {
        List<CompletableFuture<T>> futures = Arrays.stream(suppliers)
                .map(this::executeAsync)
                .toList();

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    public <T, R> CompletableFuture<R> executeAndThen(
            Supplier<T> first,
            Function<T, CompletableFuture<R>> second) {
        return executeAsyncVirtual(first).thenCompose(second);
    }

    public <T> CompletableFuture<T> executeWithTimeout(Supplier<T> supplier, Duration timeout) {
        return executeAsyncVirtual(supplier)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public <T> CompletableFuture<T> executeWithFallback(Supplier<T> supplier, Function<Throwable, T> fallback) {
        return executeAsyncVirtual(supplier)
                .exceptionally(fallback);
    }

    @SafeVarargs
    public final <T> CompletableFuture<T> executeAnyAsync(Supplier<T>... suppliers) {
        CompletableFuture<T>[] futures = Arrays.stream(suppliers)
                .map(this::executeAsyncVirtual)
                .toArray(CompletableFuture[]::new);

        // anyOf returns CompletableFuture<Object>, so we safely map it back to T.
        return CompletableFuture.anyOf(futures)
                .thenApply(result -> (T) result);
    }

    @PreDestroy
    public void shutdown() {
        platformExecutor.shutdown();
        virtualExecutor.shutdown();
    }
}
