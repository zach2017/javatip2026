package com.example.audit.publisher;

import com.example.audit.persistence.AuditEvent;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fan-out point for audit records.
 *
 * Today it writes to two sinks:
 *   1. SLF4J (synchronous, via the dedicated "AUDIT" logger)
 *   2. H2 / JPA (async, via AuditEventWriter)
 *
 * To add Kafka, Splunk, SNS, etc., add another collaborator and call it
 * from publish(). The AuditAspect never has to change.
 */
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    private final AuditEventWriter writer;

    public void publish(AuditRecord record) {
        logToConsole(record);
        writer.saveAsync(record);
    }

    private void logToConsole(AuditRecord r) {
        if (r.status() == AuditEvent.Status.SUCCESS) {
            AUDIT_LOG.info("action={} status={} method={} tookMs={} detail=\"{}\"",
                    r.action(), r.status(), r.methodName(), r.tookMs(), r.detail());
        } else {
            AUDIT_LOG.warn("action={} status={} method={} tookMs={} detail=\"{}\"",
                    r.action(), r.status(), r.methodName(), r.tookMs(), r.detail());
        }
    }
}
