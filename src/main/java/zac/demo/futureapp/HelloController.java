package zac.demo.futureapp;

import zac.demo.futureapp.HelloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller demonstrating async endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/hello")
@RequiredArgsConstructor
public class HelloController {

    private final HelloService helloService;

    /**
     * Simple async hello
     * GET /api/hello
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<String>> sayHello() {
        log.info("Received request: sayHello");
        return helloService.sayHelloAsync()
                .thenApply(v -> ResponseEntity.ok("Hello task completed!"));
    }

    /**
     * Hello using virtual threads (Java 21)
     * GET /api/hello/virtual
     */
    @GetMapping("/virtual")
    public CompletableFuture<ResponseEntity<String>> sayHelloVirtual() {
        log.info("Received request: sayHelloVirtual");
        return helloService.sayHelloVirtualAsync()
                .thenApply(v -> ResponseEntity.ok("Hello virtual thread task completed!"));
    }

    /**
     * Greet with name
     * GET /api/hello/greet?name=John
     */
    @GetMapping("/greet")
    public CompletableFuture<ResponseEntity<String>> greet(@RequestParam String name) {
        log.info("Received request: greet with name={}", name);
        return helloService.greetAsync(name)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Greet with name using virtual threads
     * GET /api/hello/greet/virtual?name=John
     */
    @GetMapping("/greet/virtual")
    public CompletableFuture<ResponseEntity<String>> greetVirtual(@RequestParam String name) {
        log.info("Received request: greetVirtual with name={}", name);
        return helloService.greetAsyncVirtual(name)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Transform name
     * GET /api/hello/transform?name=John
     */
    @GetMapping("/transform")
    public CompletableFuture<ResponseEntity<String>> transform(@RequestParam String name) {
        log.info("Received request: transform with name={}", name);
        return helloService.transformNameAsync(name)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Log a message
     * POST /api/hello/log
     */
    @PostMapping("/log")
    public CompletableFuture<ResponseEntity<String>> logMessage(@RequestBody String message) {
        log.info("Received request: logMessage");
        return helloService.logMessageAsync(message)
                .thenApply(v -> ResponseEntity.ok("Message logged successfully"));
    }

    /**
     * Run parallel tasks (platform threads)
     * GET /api/hello/parallel
     */
    @GetMapping("/parallel")
    public CompletableFuture<ResponseEntity<String>> runParallel() {
        log.info("Received request: runParallel");
        return helloService.runParallelHellosAsync()
                .thenApply(results -> ResponseEntity.ok(
                        "Parallel tasks completed: " + Arrays.toString(results)));
    }

    /**
     * Run parallel tasks (virtual threads - Java 21)
     * GET /api/hello/parallel/virtual
     */
    @GetMapping("/parallel/virtual")
    public CompletableFuture<ResponseEntity<String>> runParallelVirtual() {
        log.info("Received request: runParallelVirtual");
        return helloService.runParallelHellosVirtualAsync()
                .thenApply(results -> ResponseEntity.ok(
                        "Virtual parallel tasks completed: " + Arrays.toString(results)));
    }

    /**
     * Chain async operations
     * GET /api/hello/chain?name=John
     */
    @GetMapping("/chain")
    public CompletableFuture<ResponseEntity<String>> chain(@RequestParam String name) {
        log.info("Received request: chain with name={}", name);
        return helloService.chainedHelloAsync(name)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Execute with timeout
     * GET /api/hello/timeout?name=John&timeoutMs=1000
     */
    @GetMapping("/timeout")
    public CompletableFuture<ResponseEntity<String>> withTimeout(
            @RequestParam String name,
            @RequestParam(defaultValue = "1000") long timeoutMs) {
        log.info("Received request: timeout with name={}, timeout={}ms", name, timeoutMs);
        return helloService.greetWithTimeoutAsync(name, timeoutMs)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.status(408).body("Timeout: " + ex.getMessage()));
    }

    /**
     * Execute with fallback
     * GET /api/hello/fallback?name=John&fail=false
     */
    @GetMapping("/fallback")
    public CompletableFuture<ResponseEntity<String>> withFallback(
            @RequestParam String name,
            @RequestParam(defaultValue = "false") boolean fail) {
        log.info("Received request: fallback with name={}, fail={}", name, fail);
        return helloService.greetWithFallbackAsync(name, fail)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Race multiple tasks - returns first completed
     * GET /api/hello/race
     */
    @GetMapping("/race")
    public CompletableFuture<ResponseEntity<String>> race() {
        log.info("Received request: race");
        return helloService.raceHellosAsync()
                .thenApply(winner -> ResponseEntity.ok("Winner: " + winner));
    }
}
