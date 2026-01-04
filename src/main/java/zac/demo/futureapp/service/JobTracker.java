package zac.demo.futureapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import zac.demo.futureapp.types.*;


/**
 * Simple in-memory job tracker.
 *
 * Notes:
 * - In production, you'd usually persist this (DB/Redis) and add auth/tenancy.
 * - This implementation is thread-safe and works well for demos/local services.
 */
@Slf4j
@Service
public class JobTracker {

    private static final class MutableJob {
        final UUID id;
        final String name;
        final Instant createdAt = Instant.now();
        volatile Instant startedAt;
        volatile Instant finishedAt;
        volatile JobStatus status = JobStatus.QUEUED;
        volatile String result;
        volatile String error;

        MutableJob(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        JobSnapshot snapshot() {
            return new JobSnapshot(id, name, status, createdAt, startedAt, finishedAt, result, error);
        }
    }

    private final ConcurrentHashMap<UUID, MutableJob> jobs = new ConcurrentHashMap<>();

    /**
     * Submit a job. The supplier should start the async work and return a CompletableFuture.
     * This method returns immediately with a jobId.
     */
    public UUID submit(String jobName, Supplier<CompletableFuture<String>> work) {
        UUID id = UUID.randomUUID();
        MutableJob job = new MutableJob(id, jobName);
        jobs.put(id, job);

        // Mark running when we actually start the async pipeline
        CompletableFuture<String> future;
        try {
            job.status = JobStatus.RUNNING;
            job.startedAt = Instant.now();
            future = work.get();
        } catch (Exception e) {
            job.status = JobStatus.FAILED;
            job.finishedAt = Instant.now();
            job.error = e.getMessage();
            log.warn("Job {} failed to start: {}", id, e.toString());
            return id;
        }

        future.whenComplete((value, ex) -> {
            job.finishedAt = Instant.now();
            if (ex == null) {
                job.status = JobStatus.SUCCEEDED;
                job.result = value;
            } else {
                job.status = JobStatus.FAILED;
                job.error = rootMessage(ex);
                log.warn("Job {} failed: {}", id, job.error);
            }
        });

        return id;
    }

    public Optional<JobSnapshot> get(UUID id) {
        MutableJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(job.snapshot());
    }

    public List<JobSnapshot> list(int limit) {
        ArrayList<JobSnapshot> out = new ArrayList<>();
        for (MutableJob j : jobs.values()) out.add(j.snapshot());
        out.sort(Comparator.comparing(JobSnapshot::createdAt).reversed());
        if (limit <= 0 || out.size() <= limit) return out;
        return out.subList(0, limit);
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        String msg = cur.getMessage();
        return (msg == null || msg.isBlank()) ? cur.getClass().getSimpleName() : msg;
    }
}
