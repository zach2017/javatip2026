package zac.demo.futureapp;

import zac.demo.futureapp.HelloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates async functionality on application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncDemoRunner implements CommandLineRunner {

    private final HelloService helloService;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("Starting Async Demo Examples (Java 21)");
        log.info("========================================");

        // Example 1: Simple hello (Runnable) - Platform Thread
        log.info("\n--- Example 1: Simple Hello (Platform Thread) ---");
        CompletableFuture<Void> helloFuture = helloService.sayHelloAsync();

        // Example 2: Simple hello (Runnable) - Virtual Thread (Java 21)
        log.info("\n--- Example 2: Simple Hello (Virtual Thread - Java 21) ---");
        CompletableFuture<Void> helloVirtualFuture = helloService.sayHelloVirtualAsync();

        // Example 3: Greet with name (Supplier)
        log.info("\n--- Example 3: Greet with Name (Supplier) ---");
        CompletableFuture<String> greetFuture = helloService.greetAsync("World");

        // Example 4: Greet with name (Virtual Thread)
        log.info("\n--- Example 4: Greet with Name (Virtual Thread) ---");
        CompletableFuture<String> greetVirtualFuture = helloService.greetAsyncVirtual("Java 21");

        // Example 5: Transform name (Function)
        log.info("\n--- Example 5: Transform Name (Function) ---");
        CompletableFuture<String> transformFuture = helloService.transformNameAsync("spring");

        // Example 6: Log message (Consumer)
        log.info("\n--- Example 6: Log Message (Consumer) ---");
        CompletableFuture<Void> logFuture = helloService.logMessageAsync("Testing async consumer");

        // Wait for individual tasks and show results
        helloFuture.join();
        helloVirtualFuture.join();
        log.info("Greet result: {}", greetFuture.join());
        log.info("Greet virtual result: {}", greetVirtualFuture.join());
        log.info("Transform result: {}", transformFuture.join());
        logFuture.join();

        // Example 7: Parallel execution (Platform Threads)
        log.info("\n--- Example 7: Parallel Execution (Platform Threads) ---");
        CompletableFuture<Object[]> parallelFuture = helloService.runParallelHellosAsync();
        Object[] results = parallelFuture.join();
        log.info("Parallel results: {}", Arrays.toString(results));

        // Example 8: Parallel execution (Virtual Threads - Java 21)
        log.info("\n--- Example 8: Parallel Execution (Virtual Threads) ---");
        CompletableFuture<Object[]> parallelVirtualFuture = helloService.runParallelHellosVirtualAsync();
        Object[] virtualResults = parallelVirtualFuture.join();
        log.info("Virtual parallel results: {}", Arrays.toString(virtualResults));

        // Example 9: Chained operations
        log.info("\n--- Example 9: Chained Operations ---");
        CompletableFuture<String> chainFuture = helloService.chainedHelloAsync("  JOHN DOE  ");
        log.info("Chained result: {}", chainFuture.join());

        // Example 10: With timeout
        log.info("\n--- Example 10: With Timeout ---");
        try {
            CompletableFuture<String> timeoutFuture = helloService.greetWithTimeoutAsync("Timeout Test", 2000);
            log.info("Timeout result: {}", timeoutFuture.join());
        } catch (Exception e) {
            log.warn("Timeout example caught: {}", e.getMessage());
        }

        // Example 11: With fallback (success case)
        log.info("\n--- Example 11: With Fallback (Success) ---");
        CompletableFuture<String> fallbackSuccessFuture = helloService.greetWithFallbackAsync("Success", false);
        log.info("Fallback success result: {}", fallbackSuccessFuture.join());

        // Example 12: With fallback (failure case)
        log.info("\n--- Example 12: With Fallback (Failure) ---");
        CompletableFuture<String> fallbackFailFuture = helloService.greetWithFallbackAsync("Fail", true);
        log.info("Fallback failure result: {}", fallbackFailFuture.join());

        // Example 13: Race (first to complete wins)
        log.info("\n--- Example 13: Race (First Wins) ---");
        CompletableFuture<String> raceFuture = helloService.raceHellosAsync();
        log.info("Race winner: {}", raceFuture.join());

        log.info("\n========================================");
        log.info("Async Demo Examples Completed!");
        log.info("========================================");
        log.info("REST API available at: http://localhost:8080/api/hello");
        log.info("");
        log.info("Available Endpoints:");
        log.info("  GET  /api/hello                    - Simple async hello");
        log.info("  GET  /api/hello/virtual            - Virtual thread hello (Java 21)");
        log.info("  GET  /api/hello/greet?name=X       - Greet with name");
        log.info("  GET  /api/hello/greet/virtual?name=X - Virtual thread greet");
        log.info("  GET  /api/hello/transform?name=X   - Transform name");
        log.info("  POST /api/hello/log                - Log a message");
        log.info("  GET  /api/hello/parallel           - Run parallel tasks");
        log.info("  GET  /api/hello/parallel/virtual   - Parallel with virtual threads");
        log.info("  GET  /api/hello/chain?name=X       - Chain operations");
        log.info("  GET  /api/hello/timeout?name=X&timeoutMs=1000 - With timeout");
        log.info("  GET  /api/hello/fallback?name=X&fail=false    - With fallback");
        log.info("  GET  /api/hello/race               - Race (first wins)");
    }
}
