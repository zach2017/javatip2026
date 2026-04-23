package com.example.audit.web;

import com.example.audit.actions.CreateUserAudit;
import com.example.audit.actions.GetUserAudit;
import com.example.audit.actions.ProcessUserAudit;
import com.example.audit.annotation.Audit;
import com.example.audit.service.UserProcessingService;
import com.example.audit.service.UserProcessingService.Outcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProcessingService processingService;

    private final Map<Long, String> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    // -------- Sync CRUD --------

    @PostMapping
    @Audit(CreateUserAudit.class)
    public Map<String, Object> create(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        long id = ids.incrementAndGet();
        store.put(id, name);
        log.debug("stored user id={} name={}", id, name);
        return Map.of("id", id, "name", name);
    }

    @GetMapping("/{id}")
    @Audit(GetUserAudit.class)
    public Map<String, Object> get(@PathVariable Long id) {
        String name = store.get(id);
        if (name == null) {
            throw new IllegalStateException("not found");
        }
        return Map.of("id", id, "name", name);
    }

    // -------- Async long-running + failure simulation --------

    /**
     * Kicks off a simulated long-running task. The aspect records SUCCESS or
     * FAILURE when the CompletableFuture settles, with tookMs reflecting the
     * real wall-clock duration.
     *
     * Query params all have defaults so a bare call works:
     *     POST /users/process
     *     POST /users/process?name=Alice&durationMs=2000&outcome=SUCCESS
     *     POST /users/process?outcome=FAIL
     *     POST /users/process?outcome=FLAKY
     *     POST /users/process?outcome=TIMEOUT&durationMs=500
     */
    @PostMapping("/process")
    @Audit(ProcessUserAudit.class)
    public CompletableFuture<Map<String, Object>> process(
            @RequestParam(defaultValue = "anon") String name,
            @RequestParam(defaultValue = "1500") long durationMs,
            @RequestParam(defaultValue = "SUCCESS") Outcome outcome) {

        return processingService
                .simulateLongRunningTask(name, Duration.ofMillis(durationMs), outcome)
                .thenApply(result -> Map.of(
                        "name", name,
                        "durationMs", durationMs,
                        "outcome", outcome.name(),
                        "result", result));
    }

    /**
     * Synchronous endpoint that forces a failure path. Useful to compare the
     * sync failure audit row against the async one.
     */
    @PostMapping("/fail")
    @Audit(CreateUserAudit.class)
    public Map<String, Object> forceFail(@RequestParam(defaultValue = "boom") String name) {
        throw new IllegalStateException("forced failure for '" + name + "'");
    }
}
