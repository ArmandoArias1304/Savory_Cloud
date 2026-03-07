package com.aatechsolutions.elgransazon.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for sending real-time backup notifications via WebSocket
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    
    private static final String BACKUP_TOPIC = "/topic/backup";
    
    /**
     * Notify clients that a backup has started
     */
    public void notifyBackupStarted(boolean isAutomatic) {
        BackupNotification notification = new BackupNotification(
            "STARTED",
            isAutomatic ? "Iniciando respaldo automático..." : "Iniciando respaldo manual...",
            null,
            null,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
        sendNotification(notification);
        log.debug("Sent backup started notification");
    }
    
    /**
     * Notify clients that a backup completed successfully
     */
    public void notifyBackupCompleted(String filename, double sizeMb) {
        BackupNotification notification = new BackupNotification(
            "COMPLETED",
            "¡Respaldo completado exitosamente!",
            filename,
            String.format("%.2f MB", sizeMb),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
        sendNotification(notification);
        log.debug("Sent backup completed notification: {}", filename);
    }
    
    /**
     * Notify clients that a backup failed
     */
    public void notifyBackupFailed(String errorMessage) {
        BackupNotification notification = new BackupNotification(
            "FAILED",
            "Error al crear respaldo: " + errorMessage,
            null,
            null,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
        sendNotification(notification);
        log.debug("Sent backup failed notification");
    }
    
    /**
     * Notify clients about backup progress (optional)
     */
    public void notifyBackupProgress(String message) {
        BackupNotification notification = new BackupNotification(
            "PROGRESS",
            message,
            null,
            null,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
        sendNotification(notification);
    }
    
    private void sendNotification(BackupNotification notification) {
        try {
            messagingTemplate.convertAndSend(BACKUP_TOPIC, notification);
        } catch (Exception e) {
            log.error("Error sending backup notification: {}", e.getMessage());
        }
    }
    
    /**
     * Record class for backup notifications
     */
    public record BackupNotification(
        String status,      // STARTED, PROGRESS, COMPLETED, FAILED
        String message,
        String filename,
        String size,
        String timestamp
    ) {}
}
