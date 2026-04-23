package com.example.audit.publisher;

import com.example.audit.persistence.AuditEvent;

/**
 * Immutable value object the aspect hands to the publisher.
 * Using a record keeps the aspect -> publisher boundary clean and typed.
 */
public record AuditRecord(
        String action,
        AuditEvent.Status status,
        String methodName,
        String detail,
        long tookMs
) {}
