package com.example.audit.annotation;

import com.example.audit.action.AuditAction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as auditable. The provided {@link AuditAction} class must be
 * a Spring bean; the aspect will resolve it at runtime and use it to emit
 * audit log entries before/after the method runs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audit {
    Class<? extends AuditAction> value();
}
