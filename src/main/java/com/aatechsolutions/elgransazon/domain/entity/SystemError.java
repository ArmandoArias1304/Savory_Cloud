package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * System Error entity
 * Tracks system errors for monitoring and debugging
 */
@Entity
@Table(name = "system_errors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SystemError implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    @Column(name = "resolved")
    @Builder.Default
    private Boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "occurred_at")
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }

    /**
     * Mark error as resolved
     */
    public void markAsResolved(String resolvedBy) {
        this.resolved = true;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
    }

    /**
     * Severity enum
     */
    public enum Severity {
        LOW("Bajo"),
        MEDIUM("Medio"),
        HIGH("Alto"),
        CRITICAL("Crítico");

        private final String displayName;

        Severity(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Get severity display name
     */
    public String getSeverityDisplayName() {
        return severity.getDisplayName();
    }

    /**
     * Get icon for severity
     */
    public String getSeverityIcon() {
        switch (severity) {
            case CRITICAL: return "❌";
            case HIGH: return "⚠️";
            case MEDIUM: return "📝";
            case LOW: return "ℹ️";
            default: return "•";
        }
    }

    /**
     * Get CSS class for severity
     */
    public String getSeverityClass() {
        switch (severity) {
            case CRITICAL: return "text-danger";
            case HIGH: return "text-warning";
            case MEDIUM: return "text-info";
            case LOW: return "text-secondary";
            default: return "";
        }
    }
}
