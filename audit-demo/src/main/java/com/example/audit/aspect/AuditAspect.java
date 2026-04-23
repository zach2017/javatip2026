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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Intercepts @Audit-annotated methods.
 *
 * Handles both synchronous returns and CompletableFuture returns. For futures,
 * the record is published when the future settles (so SUCCESS/FAILURE and
 * tookMs reflect the real async outcome, not the ~0ms handoff).
 *
 * Captures the authenticated user from the SecurityContext at dispatch time
 * and passes it through to the publisher.
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
        String principal = currentPrincipal();
        long start = System.nanoTime();

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            publishFailure(action, pjp, methodName, principal, t, start);
            throw t;
        }

        if (result instanceof CompletableFuture<?> future) {
            return future.whenComplete((value, error) -> {
                if (error != null) {
                    Throwable cause = (error instanceof CompletionException && error.getCause() != null)
                            ? error.getCause() : error;
                    publishFailure(action, pjp, methodName, principal, cause, start);
                } else {
                    publishSuccess(action, pjp, methodName, principal, value, start);
                }
            });
        }

        publishSuccess(action, pjp, methodName, principal, result, start);
        return result;
    }

    private static String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "anonymous";
        return auth.getName();
    }

    private void publishSuccess(AuditAction action, ProceedingJoinPoint pjp,
                                String methodName, String principal, Object value, long startNanos) {
        publisher.publish(new AuditRecord(
                action.action(),
                AuditEvent.Status.SUCCESS,
                principal,
                methodName,
                action.formatSuccess(pjp, value),
                elapsedMs(startNanos)));
    }

    private void publishFailure(AuditAction action, ProceedingJoinPoint pjp,
                                String methodName, String principal, Throwable error, long startNanos) {
        publisher.publish(new AuditRecord(
                action.action(),
                AuditEvent.Status.FAILURE,
                principal,
                methodName,
                action.formatFailure(pjp, error),
                elapsedMs(startNanos)));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
