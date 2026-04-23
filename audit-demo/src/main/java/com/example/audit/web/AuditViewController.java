package com.example.audit.web;

import com.example.audit.persistence.AuditEvent;
import com.example.audit.persistence.AuditEventRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints for inspecting persisted audit events.
 *
 * Intentionally not annotated with @Audit — reading the audit log should
 * not itself write to the audit log.
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditViewController {

    private final AuditEventRepository repository;

    /** GET /audit?page=0&size=20&action=USER_CREATE&status=SUCCESS */
    @GetMapping
    public Page<AuditEvent> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditEvent.Status status) {

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (action != null) {
            return repository.findByAction(action, pageable);
        }
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }

    @GetMapping("/{id}")
    public AuditEvent get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("audit event not found: " + id));
    }
}
