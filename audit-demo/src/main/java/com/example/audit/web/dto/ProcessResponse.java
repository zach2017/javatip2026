package com.example.audit.web.dto;

import com.example.audit.service.UserProcessingService.Outcome;
import lombok.Builder;
import lombok.Value;

/**
 * Response for the async long-running task.
 * @Builder.Default lets us give fields sensible defaults inside the builder,
 * which is the Lombok-idiomatic way to do default args on immutable types.
 */
@Value
@Builder
public class ProcessResponse {
    String name;

    @Builder.Default
    long durationMs = 1500L;

    @Builder.Default
    Outcome outcome = Outcome.SUCCESS;

    String result;
}
