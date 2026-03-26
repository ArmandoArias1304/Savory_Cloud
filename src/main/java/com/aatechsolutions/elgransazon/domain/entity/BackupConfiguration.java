package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * BackupConfiguration entity for managing database backup settings
 * GLOBAL configuration - only ONE record exists for the entire system
 * Managed exclusively by PROGRAMMER role
 */
@Entity
@Table(name = "backup_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString
public class BackupConfiguration implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // NOTE: This is a GLOBAL configuration (no company relationship)
    // The backup includes ALL companies' data in a single database dump

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @NotNull(message = "La frecuencia de respaldo es obligatoria")
    @Min(value = 1, message = "La frecuencia debe ser al menos 1 día")
    @Max(value = 30, message = "La frecuencia no puede exceder los 30 días")
    @Column(name = "frequency_days", nullable = false)
    @Builder.Default
    private Integer frequencyDays = 7; // Default: weekly

    @NotNull(message = "La hora de respaldo es obligatoria")
    @Column(name = "backup_time", nullable = false)
    @Builder.Default
    private LocalTime backupTime = LocalTime.of(2, 0); // Default: 2:00 AM

    @NotNull(message = "El número de respaldos a conservar es obligatorio")
    @Min(value = 1, message = "El número de respaldos a conservar debe ser al menos 1")
    @Max(value = 30, message = "El número de respaldos a conservar no puede exceder los 30")
    @Column(name = "retention_count", nullable = false)
    @Builder.Default
    private Integer retentionCount = 10; // Default: keep last 10 backups

    @Column(name = "backup_path", length = 500)
    @Builder.Default
    private String backupPath = "backups"; // Relative to user home or app root

    @Column(name = "last_backup_date")
    private LocalDateTime lastBackupDate;

    @Column(name = "last_backup_status", length = 50)
    private String lastBackupStatus; // SUCCESS, FAILED

    @Column(name = "last_backup_filename", length = 255)
    private String lastBackupFilename;

    @Column(name = "last_backup_size_mb")
    private Double lastBackupSizeMb;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
