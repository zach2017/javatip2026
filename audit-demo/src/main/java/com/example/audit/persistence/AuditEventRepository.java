package com.example.audit.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByAction(String action, Pageable pageable);

    Page<AuditEvent> findByStatus(AuditEvent.Status status, Pageable pageable);
}
