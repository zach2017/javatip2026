package com.example.audit.aspect;

import com.example.audit.action.AuditAction;
import com.example.audit.annotation.Audit;
import com.example.audit.persistence.AuditEvent;
import com.example.audit.publisher.AuditEventPublisher;
import com.example.audit.publisher.AuditRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Intercepts @Audit-annotated methods.
 *
 * Two return-type flavours are supported:
 *   - plain value: audit immediately after the method returns (or throws).
 *   - CompletableFuture<T>: audit when the future completes. The method
 *     returns instantly; success/failure is decided by the future, not the
 *     synchronous call. This is what makes long-running operations auditable.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final ApplicationContext context;
    private final AuditEventPublisher publisher;

    @Around("@annotation(audit)")
    public Object audit(ProceedingJoinPoint pjp, Audit audit) throws Throwable {
        AuditAction action = context.getBean(audit.value());
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String methodName = signature.getMethod().getName();
        long start = System.nanoTime();

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            publishFailure(action, pjp, methodName, t, start);
            throw t;
        }

        // Async path: attach callback, publish when the future settles.
        if (result instanceof CompletableFuture<?> future) {
            return future.whenComplete((value, error) -> {
                if (error != null) {
                    // CompletableFuture wraps in CompletionException; unwrap for nicer messages.
                    Throwable cause = (error instanceof CompletionException && error.getCause() != null)
                            ? error.getCause() : error;
                    publishFailure(action, pjp, methodName, cause, start);
                } else {
                    publishSuccess(action, pjp, methodName, value, start);
                }
            });
        }

        // Sync path.
        publishSuccess(action, pjp, methodName, result, start);
        return result;
    }

    private void publishSuccess(AuditAction action, ProceedingJoinPoint pjp,
                                String methodName, Object value, long startNanos) {
        publisher.publish(new AuditRecord(
                action.action(),
                AuditEvent.Status.SUCCESS,
                methodName,
                action.formatSuccess(pjp, value),
                elapsedMs(startNanos)));
    }

    private void publishFailure(AuditAction action, ProceedingJoinPoint pjp,
                                String methodName, Throwable error, long startNanos) {
        publisher.publish(new AuditRecord(
                action.action(),
                AuditEvent.Status.FAILURE,
                methodName,
                action.formatFailure(pjp, error),
                elapsedMs(startNanos)));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
