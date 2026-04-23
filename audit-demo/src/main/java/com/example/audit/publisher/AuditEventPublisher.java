package com.example.audit.publisher;

import com.example.audit.persistence.AuditEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fan-out point for audit records.
 *
 * Sinks:
 *   1. SLF4J "AUDIT" logger (synchronous, via @Slf4j(topic = "AUDIT"))
 *   2. H2 / JPA via AuditEventWriter (@Async)
 *
 * Add new sinks as collaborators and call them from publish(); the aspect
 * never changes.
 */
@Slf4j(topic = "AUDIT")
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final AuditEventWriter writer;

    public void publish(AuditRecord record) {
        logToConsole(record);
        writer.saveAsync(record);
    }

    private void logToConsole(AuditRecord r) {
        if (r.status() == AuditEvent.Status.SUCCESS) {
            log.info("action={} status={} method={} tookMs={} detail=\"{}\"",
                    r.action(), r.status(), r.methodName(), r.tookMs(), r.detail());
        } else {
            log.warn("action={} status={} method={} tookMs={} detail=\"{}\"",
                    r.action(), r.status(), r.methodName(), r.tookMs(), r.detail());
        }
    }
}
