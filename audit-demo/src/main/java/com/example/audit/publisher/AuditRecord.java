package com.example.audit.publisher;

import com.example.audit.persistence.AuditEvent;

/**
 * Immutable value object the aspect hands to the publisher.
 */
public record AuditRecord(
        String action,
        AuditEvent.Status status,
        String principal,
        String methodName,
        String detail,
        long tookMs
) {}
