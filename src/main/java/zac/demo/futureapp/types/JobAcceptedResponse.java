package zac.demo.futureapp.types;

import java.util.UUID;

/**
 * Returned immediately (202 Accepted) when a job is started.
 */
public record JobAcceptedResponse(
        UUID jobId,
        String statusUrl
) {
    public static JobAcceptedResponse from(UUID jobId, String statusUrl) {
        return new JobAcceptedResponse(jobId, statusUrl);
    }
}
