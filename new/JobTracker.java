package zac.demo.futureapp.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import zac.demo.futureapp.types.JobSnapshot;
import zac.demo.futureapp.types.JobStatus;

/**
 * JobTracker - Manages async jobs and their lifecycle.
 * 
 * HOW IT WORKS:
 * =============
 * 
 * 1. When you call submit():
 *    - A new UUID is generated
 *    - A Job record is created with status PENDING
 *    - The async work is started (but NOT waited for)
 *    - The UUID is returned immediately
 * 
 * 2. The async work runs in background:
 *    - Status changes to RUNNING
 *    - When complete, status becomes COMPLETED or FAILED
 *    - Result or error is stored in the Job
 * 
 * 3. Clients can poll get(jobId) to check status
 * 
 * EXAMPLE USAGE:
 * ==============
 * 
 * // In your service:
 * UUID jobId = jobTracker.submit("myJob", () -> 
 *     asyncExecutor.executeAsyncVirtual(() -> {
 *         return callSlowApi();
 *     })
 * );
 * 
 * // In your controller:
 * return ResponseEntity.accepted().body(Map.of("jobId", jobId));
 * 
 * // Client polls:
 * GET /api/jobs/{jobId}
 */
@Slf4j
@Service
public class JobTracker {

    // In-memory storage (use Redis/DB in production)
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    /**
     * Submit a new async job.
     * 
     * @param name            A descriptive name for the job (for logging)
     * @param futureSupplier  A supplier that returns a CompletableFuture
     * @return The job ID (UUID) - returned immediately, work runs in background
     * 
     * HOW THIS WORKS:
     * ---------------
     * 1. futureSupplier.get() is called, which starts the async work
     * 2. This returns a CompletableFuture immediately (work is running)
     * 3. We attach a callback with whenComplete() to update status when done
     * 4. We return the UUID immediately - don't wait for the work to finish
     */
    public UUID submit(String name, Supplier<CompletableFuture<?>> futureSupplier) {
        // Step 1: Generate unique ID
        UUID jobId = UUID.randomUUID();
        log.info("Creating job {} with name '{}'", jobId, name);

        // Step 2: Create job record with PENDING status
        Job job = new Job(jobId, name);
        jobs.put(jobId, job);

        // Step 3: Start the async work (this returns immediately)
        CompletableFuture<?> future;
        try {
            job.setStatus(JobStatus.RUNNING);
            future = futureSupplier.get();  // This starts the work
        } catch (Exception e) {
            log.error("Failed to start job {}: {}", jobId, e.getMessage());
            job.setStatus(JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setCompletedAt(Instant.now());
            return jobId;
        }

        // Step 4: Attach completion handler (runs when work finishes)
        future.whenComplete((result, error) -> {
            if (error != null) {
                log.error("Job {} failed: {}", jobId, error.getMessage());
                job.setStatus(JobStatus.FAILED);
                job.setError(error.getMessage());
            } else {
                log.info("Job {} completed successfully", jobId);
                job.setStatus(JobStatus.COMPLETED);
                job.setResult(result);
            }
            job.setCompletedAt(Instant.now());
        });

        // Step 5: Return ID immediately (work continues in background)
        log.info("Job {} submitted, returning immediately", jobId);
        return jobId;
    }

    /**
     * Get job status by ID.
     * 
     * @param jobId The job UUID
     * @return Optional containing job snapshot, or empty if not found
     */
    public Optional<JobSnapshot> get(UUID jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            return Optional.empty();
        }
        return Optional.of(job.toSnapshot());
    }

    /**
     * List recent jobs.
     * 
     * @param limit Maximum number of jobs to return
     * @return List of job snapshots, most recent first
     */
    public List<JobSnapshot> list(int limit) {
        return jobs.values().stream()
                .sorted(Comparator.comparing(Job::getCreatedAt).reversed())
                .limit(limit)
                .map(Job::toSnapshot)
                .toList();
    }

    /**
     * Clear completed/failed jobs older than specified duration.
     * Call this periodically to prevent memory leaks.
     */
    public int cleanup(java.time.Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        int removed = 0;
        
        var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Job job = entry.getValue();
            
            boolean isFinished = job.getStatus() == JobStatus.COMPLETED 
                              || job.getStatus() == JobStatus.FAILED;
            boolean isOld = job.getCompletedAt() != null 
                         && job.getCompletedAt().isBefore(cutoff);
            
            if (isFinished && isOld) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned up {} old jobs", removed);
        }
        return removed;
    }

    // ========================================
    // Internal Job class
    // ========================================
    
    private static class Job {
        private final UUID id;
        private final String name;
        private final Instant createdAt;
        private volatile JobStatus status;
        private volatile Object result;
        private volatile String error;
        private volatile Instant completedAt;

        Job(UUID id, String name) {
            this.id = id;
            this.name = name;
            this.createdAt = Instant.now();
            this.status = JobStatus.PENDING;
        }

        // Getters and setters
        UUID getId() { return id; }
        String getName() { return name; }
        Instant getCreatedAt() { return createdAt; }
        JobStatus getStatus() { return status; }
        void setStatus(JobStatus status) { this.status = status; }
        Object getResult() { return result; }
        void setResult(Object result) { this.result = result; }
        String getError() { return error; }
        void setError(String error) { this.error = error; }
        Instant getCompletedAt() { return completedAt; }
        void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

        JobSnapshot toSnapshot() {
            return new JobSnapshot(
                id, name, status, result, error, createdAt, completedAt
            );
        }
    }
}
