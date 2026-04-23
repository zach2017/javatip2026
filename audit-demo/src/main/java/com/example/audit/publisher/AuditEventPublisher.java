package com.example.audit.publisher;

import com.example.audit.persistence.AuditEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
            log.info("action={} status={} user={} method={} tookMs={} detail=\"{}\"",
                    r.action(), r.status(), r.principal(), r.methodName(), r.tookMs(), r.detail());
        } else {
            log.warn("action={} status={} user={} method={} tookMs={} detail=\"{}\"",
                    r.action(), r.status(), r.principal(), r.methodName(), r.tookMs(), r.detail());
        }
    }
}
