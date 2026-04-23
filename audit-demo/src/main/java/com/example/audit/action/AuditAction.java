package com.example.audit.action;

import org.aspectj.lang.JoinPoint;

/**
 * Describes a single auditable operation.
 *
 * Each @Audit-annotated method points to one implementation of this interface
 * (registered as a Spring bean). Three pieces of information are required:
 *
 *   action()        - stable machine-readable identifier (e.g. "USER_CREATE")
 *   successFormat() - message template used when the method returns normally
 *   failureFormat() - message template used when the method throws
 *
 * Format strings use {0}, {1}, ... for method arguments, plus {result} for
 * the return value and {error} for a thrown exception's message.
 *
 * Implementations may override the default formatters to add custom logic such
 * as PII masking or pulling context from the security principal.
 */
public interface AuditAction {

    String action();

    String successFormat();

    String failureFormat();

    default String formatSuccess(JoinPoint jp, Object result) {
        return AuditFormatter.format(successFormat(), jp.getArgs(), result, null);
    }

    default String formatFailure(JoinPoint jp, Throwable error) {
        return AuditFormatter.format(failureFormat(), jp.getArgs(), null, error);
    }
}
