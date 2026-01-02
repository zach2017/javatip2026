package zac.demo.futureapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Example service demonstrating how to use AsyncExecutorService
 * with simple hello logging examples.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelloService {

    private final AsyncExecutorService asyncExecutorService;

    /**
     * Simple hello log using Runnable (no return value)
     */
    public CompletableFuture<Void> sayHelloAsync() {
        return asyncExecutorService.executeAsync(() -> {
            log.info("Hello from async task!");
            simulateWork(10000);
            log.info("Hello task completed!");
        });
    }

    /**
     * Hello using virtual threads (Java 21) - great for I/O
     */
    public CompletableFuture<Void> sayHelloVirtualAsync() {
        return asyncExecutorService.executeAsyncVirtual(() -> {
            log.info("Hello from VIRTUAL thread task!");
            simulateWork(1000);
            log.info("Hello virtual thread task completed!");
        });
    }

    /**
     * Hello with name using Supplier (returns a value)
     */
    public CompletableFuture<String> greetAsync(String name) {
        return asyncExecutorService.executeAsync(() -> {
            log.info("Hello, preparing greeting for: {}", name);
            simulateWork(500);
            String greeting = "Hello, " + name + "! Welcome to async world!";
            log.info("Greeting created: {}", greeting);
            return greeting;
        });
    }

    /**
     * Greet using virtual threads - ideal for I/O-bound operations
     */
    public CompletableFuture<String> greetAsyncVirtual(String name) {
        return asyncExecutorService.executeAsyncVirtual(() -> {
            log.info("Hello, preparing greeting on virtual thread for: {}", name);
            simulateWork(500);
            String greeting = "Hello, " + name + "! Welcome to virtual thread world!";
            log.info("Greeting created on virtual thread: {}", greeting);
            return greeting;
        });
    }

    /**
     * Transform input using Function
     */
    public CompletableFuture<String> transformNameAsync(String name) {
        return asyncExecutorService.executeAsync(
                (String input) -> {
                    log.info("Hello, transforming name: {}", input);
                    simulateWork(300);
                    String result = input.toUpperCase() + " - TRANSFORMED";
                    log.info("Hello, transformation result: {}", result);
                    return result;
                },
                name);
    }

    /**
     * Consume and log using Consumer
     */
    public CompletableFuture<Void> logMessageAsync(String message) {
        return asyncExecutorService.executeAsync(
                (String msg) -> {
                    log.info("Hello Logger - Received message: {}", msg);
                    simulateWork(200);
                    log.info("Hello Logger - Message processed successfully");
                },
                message);
    }

    /**
     * Run multiple parallel tasks using platform threads
     */
    public CompletableFuture<Object[]> runParallelHellosAsync() {
        return asyncExecutorService.executeAllAsync(
                () -> {
                    log.info("Hello from Task 1!");
                    simulateWork(1000);
                    return "Result from Task 1";
                },
                () -> {
                    log.info("Hello from Task 2!");
                    simulateWork(800);
                    return "Result from Task 2";
                },
                () -> {
                    log.info("Hello from Task 3!");
                    simulateWork(600);
                    return "Result from Task 3";
                });
    }

    /**
     * Run multiple parallel tasks using virtual threads (Java 21)
     * Can scale to thousands of concurrent tasks efficiently
     */
    public CompletableFuture<Object[]> runParallelHellosVirtualAsync() {
        return asyncExecutorService.executeAllAsyncVirtual(
                () -> {
                    log.info("Hello from Virtual Task 1!");
                    simulateWork(1000);
                    return "Virtual Result 1";
                },
                () -> {
                    log.info("Hello from Virtual Task 2!");
                    simulateWork(800);
                    return "Virtual Result 2";
                },
                () -> {
                    log.info("Hello from Virtual Task 3!");
                    simulateWork(600);
                    return "Virtual Result 3";
                });
    }

    /**
     * Chain async operations
     */
    public CompletableFuture<String> chainedHelloAsync(String name) {
        return asyncExecutorService.executeAndThen(
                () -> {
                    log.info("Hello Step 1: Preparing name {}", name);
                    simulateWork(500);
                    return name.trim().toLowerCase();
                },
                (String normalized) -> {
                    log.info("Hello Step 2: Formatting normalized name {}", normalized);
                    simulateWork(300);
                    return "Hello, " + capitalize(normalized) + "!";
                });
    }

    /**
     * Execute with timeout
     */
    public CompletableFuture<String> greetWithTimeoutAsync(String name, long timeoutMs) {
        return asyncExecutorService.executeWithTimeout(
                () -> {
                    log.info("Hello, greeting {} with timeout", name);
                    simulateWork(500);
                    return "Hello, " + name + "!";
                },
                Duration.ofMillis(timeoutMs));
    }

    /**
     * Execute with fallback on failure
     */
    public CompletableFuture<String> greetWithFallbackAsync(String name, boolean shouldFail) {
        return asyncExecutorService.executeWithFallback(
                () -> {
                    log.info("Hello, attempting to greet: {}", name);
                    if (shouldFail) {
                        throw new RuntimeException("Simulated failure!");
                    }
                    return "Hello, " + name + "!";
                },
                ex -> {
                    log.warn("Using fallback greeting due to: {}", ex.getMessage());
                    return "Hello, Guest! (fallback)";
                });
    }

    /**
     * Race multiple tasks - returns first completed
     */
    public CompletableFuture<String> raceHellosAsync() {
        return asyncExecutorService.executeAnyAsync(
                () -> {
                    log.info("Hello racer 1 starting (slow)");
                    simulateWork(2000);
                    return "Racer 1 finished";
                },
                () -> {
                    log.info("Hello racer 2 starting (fast)");
                    simulateWork(500);
                    return "Racer 2 finished";
                },
                () -> {
                    log.info("Hello racer 3 starting (medium)");
                    simulateWork(1000);
                    return "Racer 3 finished";
                });
    }

    private void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Task interrupted");
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
