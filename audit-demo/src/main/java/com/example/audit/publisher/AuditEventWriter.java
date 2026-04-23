package com.example.audit.publisher;

import com.example.audit.persistence.AuditEvent;
import com.example.audit.persistence.AuditEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persists audit events asynchronously.
 *
 * Lives in a separate bean from AuditEventPublisher because Spring's @Async
 * only works through the proxy — self-invocation (one method in a bean calling
 * another @Async method on the same bean) bypasses the proxy and runs
 * synchronously. Putting the @Async method on a distinct bean guarantees the
 * save actually happens on the async executor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventWriter {

    private final AuditEventRepository repository;

    @Async
    public void saveAsync(AuditRecord r) {
        try {
            repository.save(AuditEvent.builder()
                    .action(r.action())
                    .status(r.status())
                    .methodName(r.methodName())
                    .detail(r.detail())
                    .tookMs(r.tookMs())
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to persist audit event for action={}: {}", r.action(), e.getMessage(), e);
        }
    }
}
