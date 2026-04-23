package com.example.audit.service;

import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Simulates a slow backend operation (DB batch, external API, ML inference).
 *
 * Returns CompletableFuture so the HTTP thread is freed immediately. The
 * AuditAspect attaches whenComplete and records SUCCESS/FAILURE based on the
 * future's eventual outcome.
 */
@Slf4j
@Service
public class UserProcessingService {

    public CompletableFuture<String> simulateLongRunningTask(String username,
                                                              Duration duration,
                                                              Outcome outcome) {
        log.info("starting long task username={} duration={}ms outcome={}",
                username, duration.toMillis(), outcome);

        return CompletableFuture.supplyAsync(() -> runWithOutcome(username, duration, outcome));
    }

    private String runWithOutcome(String username, Duration duration, Outcome outcome) {
        sleepQuietly(duration);

        return switch (outcome) {
            case SUCCESS -> "processed '" + username + "' after " + duration.toMillis() + "ms";
            case FAIL    -> throw new IllegalStateException(
                    "downstream service rejected request for '" + username + "'");
            case TIMEOUT -> throw new SimulatedTimeoutException(
                    "external system did not respond in time for '" + username + "'");
            case FLAKY -> {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    yield "flaky-success for '" + username + "'";
                }
                throw new IllegalStateException("flaky failure for '" + username + "'");
            }
        };
    }

    private static void sleepQuietly(Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("task interrupted", e);
        }
    }

    public enum Outcome { SUCCESS, FAIL, TIMEOUT, FLAKY }

    /**
     * Lombok @StandardException generates the four canonical constructors
     * (no-arg, message, cause, message+cause) — replacing ~15 lines of
     * boilerplate with one annotation.
     */
    @StandardException
    public static class SimulatedTimeoutException extends RuntimeException {}
}
