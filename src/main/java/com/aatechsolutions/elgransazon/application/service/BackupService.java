package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.BackupConfiguration;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for database backup operations
 * GLOBAL: Single backup configuration for the entire system.
 * The backup includes ALL companies' data in a single database dump.
 * Managed exclusively by PROGRAMMER role.
 */
public interface BackupService {
    
    /**
     * Get the global backup configuration
     * @return Optional containing the configuration, or empty if none exists
     */
    Optional<BackupConfiguration> getConfiguration();
    
    /**
     * Get or create the global backup configuration
     * Creates a default configuration if none exists (lazy initialization)
     * @return The global backup configuration
     */
    BackupConfiguration getOrCreateGlobalConfiguration();
    
    /**
     * Get all backup configurations (for backwards compatibility - returns single global config)
     * @return List containing the single global backup configuration
     */
    java.util.List<BackupConfiguration> getAllConfigurations();
    
    /**
     * Save backup configuration
     */
    BackupConfiguration saveConfiguration(BackupConfiguration config);
    
    /**
     * Create a manual backup immediately
     * @return the backup filename if successful, empty if failed
     */
    Optional<String> createManualBackup();
    
    /**
     * Create a scheduled backup (called by scheduler)
     */
    void createScheduledBackup();
    
    /**
     * Get list of existing backup files
     */
    List<BackupFileInfo> listBackups();
    
    /**
     * Delete a specific backup file
     */
    boolean deleteBackup(String filename);
    
    /**
     * Delete old backups exceeding retention count
     */
    void cleanOldBackups();
    
    /**
     * Get the backup directory path
     */
    String getBackupDirectory();
    
    /**
     * Get a specific backup file
     */
    Optional<File> getBackupFile(String filename);
    
    /**
     * Check if backup is currently in progress
     */
    boolean isBackupInProgress();
    
    /**
     * Inner class to represent backup file information
     */
    record BackupFileInfo(
        String filename,
        long sizeBytes,
        double sizeMb,
        java.time.LocalDateTime createdAt,
        String formattedSize,
        String formattedDate
    ) {
        public static BackupFileInfo fromFile(File file) {
            long size = file.length();
            double sizeMb = size / (1024.0 * 1024.0);
            String formattedSize = sizeMb >= 1 
                ? String.format("%.2f MB", sizeMb)
                : String.format("%.2f KB", size / 1024.0);
            
            java.time.LocalDateTime createdAt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(file.lastModified()),
                java.time.ZoneId.systemDefault()
            );
            
            String formattedDate = createdAt.format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            );
            
            return new BackupFileInfo(file.getName(), size, sizeMb, createdAt, formattedSize, formattedDate);
        }
    }
}
