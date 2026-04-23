package com.example.audit.actions;

import com.example.audit.action.AuditAction;
import org.springframework.stereotype.Component;

@Component
public class CreateUserAudit implements AuditAction {

    @Override public String action()        { return "USER_CREATE"; }
    @Override public String successFormat() { return "Created user ''{0}'' -> {result}"; }
    @Override public String failureFormat() { return "Failed to create user ''{0}'': {error}"; }
}
