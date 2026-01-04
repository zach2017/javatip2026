package zac.demo.futureapp.types;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view of a job's current state.
 */
public record JobSnapshot(
        UUID jobId,
        String name,
        JobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String result,
        String error
) {}
