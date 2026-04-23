package com.example.audit.aspect;

import com.example.audit.action.AuditAction;
import com.example.audit.annotation.Audit;
import com.example.audit.persistence.AuditEvent;
import com.example.audit.publisher.AuditEventPublisher;
import com.example.audit.publisher.AuditRecord;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Intercepts @Audit-annotated methods, invokes the method, and emits an
 * AuditRecord to the publisher. The aspect itself owns no I/O — logging and
 * persistence are the publisher's job.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final ApplicationContext context;
    private final AuditEventPublisher publisher;

    @Around("@annotation(audit)")
    public Object audit(ProceedingJoinPoint pjp, Audit audit) throws Throwable {
        AuditAction action = context.getBean(audit.value());
        String methodName = ((MethodSignature) pjp.getSignature()).getMethod().getName();
        long start = System.nanoTime();

        try {
            Object result = pjp.proceed();
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            publisher.publish(new AuditRecord(
                    action.action(),
                    AuditEvent.Status.SUCCESS,
                    methodName,
                    action.formatSuccess(pjp, result),
                    tookMs));
            return result;
        } catch (Throwable t) {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            publisher.publish(new AuditRecord(
                    action.action(),
                    AuditEvent.Status.FAILURE,
                    methodName,
                    action.formatFailure(pjp, t),
                    tookMs));
            throw t;
        }
    }
}
