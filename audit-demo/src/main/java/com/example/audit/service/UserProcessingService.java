package com.example.audit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Simulates a slow backend operation (DB batch, external API, ML inference, ...).
 *
 * Returns CompletableFuture so the HTTP thread is freed immediately while the
 * work continues on the common ForkJoinPool. The AuditAspect attaches a
 * whenComplete callback and records SUCCESS/FAILURE based on the future's
 * eventual outcome rather than the initial return.
 */
@Slf4j
@Service
public class UserProcessingService {

    /**
     * Runs for the requested duration, then either succeeds or fails based on
     * the chosen outcome. Timer lets you demonstrate the aspect capturing real
     * elapsed time for async work.
     */
    public CompletableFuture<String> simulateLongRunningTask(String username,
                                                              Duration duration,
                                                              Outcome outcome) {
        log.info("starting long task username={} duration={}ms outcome={}",
                username, duration.toMillis(), outcome);

        return CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("task interrupted", e);
            }

            return switch (outcome) {
                case SUCCESS -> "processed '" + username + "' after " + duration.toMillis() + "ms";
                case FAIL    -> throw new IllegalStateException(
                        "downstream service rejected request for '" + username + "'");
                case TIMEOUT -> throw new CompletionTimeoutException(
                        new TimeoutException("external system did not respond in time"));
                case FLAKY -> {
                    // 50/50 on whether this run succeeds — useful for demoing mixed audit rows.
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        yield "flaky-success for '" + username + "'";
                    }
                    throw new IllegalStateException("flaky failure for '" + username + "'");
                }
            };
        });
    }

    public enum Outcome { SUCCESS, FAIL, TIMEOUT, FLAKY }

    /** Unchecked wrapper so TimeoutException can travel through supplyAsync without a checked signature. */
    public static class CompletionTimeoutException extends RuntimeException {
        public CompletionTimeoutException(Throwable cause) { super(cause.getMessage(), cause); }
    }
}
