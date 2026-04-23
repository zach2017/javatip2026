package com.example.audit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persisted audit row. Written by AuditEventPublisher after each @Audit-intercepted call.
 */
@Entity
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false, length = 128)
    private String methodName;

    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private long tookMs;

    @Column(nullable = false)
    private Instant createdAt;

    public enum Status { SUCCESS, FAILURE }
}
