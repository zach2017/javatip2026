package com.example.audit.actions;

import com.example.audit.action.AuditAction;
import org.springframework.stereotype.Component;

@Component
public class GetUserAudit implements AuditAction {

    @Override public String action()        { return "USER_GET"; }
    @Override public String successFormat() { return "Fetched user id={0} -> {result}"; }
    @Override public String failureFormat() { return "Lookup failed for id={0}: {error}"; }
}
