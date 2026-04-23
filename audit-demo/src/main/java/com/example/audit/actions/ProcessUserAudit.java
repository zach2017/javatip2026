package com.example.audit.actions;

import com.example.audit.action.AuditAction;
import org.springframework.stereotype.Component;

/**
 * Audits the async "process user" task. Because the task returns
 * CompletableFuture, the aspect publishes SUCCESS/FAILURE based on the
 * future's eventual state, and tookMs reflects the real wall-clock duration
 * of the async work — not the ~0ms it took the controller to hand off.
 */
@Component
public class ProcessUserAudit implements AuditAction {

    @Override public String action()        { return "USER_PROCESS"; }
    @Override public String successFormat() { return "Processed user ''{0}'' (requested {1}ms, outcome={2}) -> {result}"; }
    @Override public String failureFormat() { return "Processing failed for ''{0}'' (requested {1}ms, outcome={2}): {error}"; }
}
