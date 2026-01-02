package zac.demo.futureapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Example service demonstrating async work with "immediate return" job tracking.
 *
 * This keeps HTTP requests fast (return 202 immediately) while work continues
 * in the background and can be polled via /api/hello/jobs/{jobId}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelloService {

    private final AsyncExecutorService asyncExecutorService;
    private final JobTracker jobTracker;

    // ------------------------
    // Job starters (fire-and-track)
    // ------------------------

    public UUID startHelloJob(String name) {
        return jobTracker.submit("hello:" + name, () ->
                asyncExecutorService.executeAsyncVirtual(() -> {
                    sleep(Duration.ofSeconds(2));
                    return "Hello, " + capitalize(name) + "!";
                })
        );
    }

    public UUID startComposeHelloJob(String name) {
        return jobTracker.submit("compose:" + name, () ->
                asyncExecutorService.executeAndThen(
                        () -> {
                            sleep(Duration.ofSeconds(1));
                            return "hello " + name;
                        },
                        base -> asyncExecutorService.executeAsyncVirtual(() -> {
                            sleep(Duration.ofSeconds(1));
                            return base + " (composed)";
                        })
                )
        );
    }

    public UUID startRaceJob() {
        return jobTracker.submit("race", () ->
                asyncExecutorService.executeAnyAsync(
                        () -> {
                            sleep(Duration.ofSeconds(3));
                            return "Slow hello";
                        },
                        () -> {
                            sleep(Duration.ofSeconds(1));
                            return "Fast hello";
                        },
                        () -> {
                            sleep(Duration.ofSeconds(2));
                            return "Medium hello";
                        }
                )
        );
    }

    // ------------------------
    // Job queries
    // ------------------------

    public Optional<JobSnapshot> getJob(UUID jobId) {
        return jobTracker.get(jobId);
    }

    public List<JobSnapshot> listJobs(int limit) {
        return jobTracker.list(limit);
    }

    // ------------------------
    // Helpers
    // ------------------------

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task interrupted", e);
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isBlank()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
