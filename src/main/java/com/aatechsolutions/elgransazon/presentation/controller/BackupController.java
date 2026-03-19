package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BackupService;
import com.aatechsolutions.elgransazon.application.service.BackupService.BackupFileInfo;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.domain.entity.BackupConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for database backup management
 */
@Controller
@RequestMapping("/programmer/backup")
@PreAuthorize("hasRole('PROGRAMMER')")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final BackupService backupService;
    private final DateTimeService dateTimeService;
    
    // Threshold for low disk space warning (in GB)
    private static final double LOW_DISK_SPACE_THRESHOLD_GB = 5.0;
    // Days without backup to trigger warning
    private static final int DAYS_WITHOUT_BACKUP_WARNING = 7;
    
    /**
     * Display backup management page
     */
    @GetMapping
    public String backupPage(Model model) {
        // Get or create the global backup configuration
        BackupConfiguration config = backupService.getOrCreateGlobalConfiguration();
        List<BackupFileInfo> backups = backupService.listBackups();
        
        model.addAttribute("config", config);
        model.addAttribute("hasConfig", true);  // Always true now (auto-created)
        model.addAttribute("backups", backups);
        model.addAttribute("backupDir", backupService.getBackupDirectory());
        model.addAttribute("backupInProgress", backupService.isBackupInProgress());
        
        // Add statistics
        model.addAttribute("stats", calculateStats(backups));
        
        // Add disk space info
        model.addAttribute("diskSpace", getDiskSpaceInfo());
        
        // Add days since last backup warning
        model.addAttribute("daysSinceLastBackup", calculateDaysSinceLastBackup(config));
        model.addAttribute("daysWithoutBackupWarning", DAYS_WITHOUT_BACKUP_WARNING);
        
        return "programmer/backup";
    }
    
    /**
     * Save backup configuration
     */
    @PostMapping("/config")
    public String saveConfig(
            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
            @RequestParam("frequencyDays") int frequencyDays,
            @RequestParam("backupTime") String backupTime,
            @RequestParam("retentionCount") int retentionCount,
            @RequestParam(value = "backupPath", required = false) String backupPath,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Get or create the global backup configuration
            BackupConfiguration config = backupService.getOrCreateGlobalConfiguration();
            config.setEnabled(enabled);
            config.setFrequencyDays(Math.max(1, Math.min(30, frequencyDays)));
            config.setBackupTime(LocalTime.parse(backupTime));
            config.setRetentionCount(Math.max(1, Math.min(30, retentionCount)));
            config.setBackupPath(backupPath != null ? backupPath.trim() : null);
            
            backupService.saveConfiguration(config);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Configuración de backup guardada correctamente");
            
        } catch (Exception e) {
            log.error("Error saving backup configuration: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al guardar la configuración: " + e.getMessage());
        }
        
        return "redirect:/programmer/backup";
    }
    
    /**
     * Create manual backup
     */
    @PostMapping("/create")
    public String createBackup(RedirectAttributes redirectAttributes) {
        if (backupService.isBackupInProgress()) {
            redirectAttributes.addFlashAttribute("warningMessage", 
                "Ya hay un backup en progreso. Por favor espere.");
            return "redirect:/programmer/backup";
        }
        
        Optional<String> result = backupService.createManualBackup();
        
        if (result.isPresent()) {
            redirectAttributes.addFlashAttribute("successMessage", 
                "Backup creado correctamente: " + result.get());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al crear el backup. Revise los logs para más detalles.");
        }
        
        return "redirect:/programmer/backup";
    }
    
    /**
     * Download a backup file
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String filename) {
        Optional<File> fileOpt = backupService.getBackupFile(filename);
        
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        File file = fileOpt.get();
        Resource resource = new FileSystemResource(file);
        
        String contentType = filename.endsWith(".gz") ? 
            "application/gzip" : "application/sql";
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + filename + "\"")
            .body(resource);
    }
    
    /**
     * Delete a backup file
     */
    @PostMapping("/delete/{filename}")
    public String deleteBackup(
            @PathVariable String filename,
            RedirectAttributes redirectAttributes) {
        
        if (backupService.deleteBackup(filename)) {
            redirectAttributes.addFlashAttribute("successMessage", 
                "Backup eliminado: " + filename);
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No se pudo eliminar el backup: " + filename);
        }
        
        return "redirect:/programmer/backup";
    }
    
    /**
     * Clean old backups
     */
    @PostMapping("/clean")
    public String cleanOldBackups(RedirectAttributes redirectAttributes) {
        try {
            backupService.cleanOldBackups();
            redirectAttributes.addFlashAttribute("successMessage", 
                "Backups antiguos eliminados correctamente");
        } catch (Exception e) {
            log.error("Error cleaning old backups: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al limpiar backups antiguos: " + e.getMessage());
        }
        
        return "redirect:/programmer/backup";
    }
    
    /**
     * AJAX endpoint to check backup status
     */
    @GetMapping("/status")
    @ResponseBody
    public BackupStatusResponse getBackupStatus() {
        Optional<BackupConfiguration> configOpt = backupService.getConfiguration();
        return new BackupStatusResponse(
            backupService.isBackupInProgress(),
            configOpt.map(BackupConfiguration::getLastBackupStatus).orElse(null),
            configOpt.map(BackupConfiguration::getLastBackupFilename).orElse(null),
            configOpt.map(c -> c.getLastBackupDate() != null ? c.getLastBackupDate().toString() : null).orElse(null)
        );
    }
    
    // Inner class for JSON response
    public record BackupStatusResponse(
        boolean inProgress,
        String lastStatus,
        String lastFilename,
        String lastDate
    ) {}
    
    /**
     * API endpoint for calendar data
     */
    @GetMapping("/api/calendar")
    @ResponseBody
    public List<CalendarEvent> getCalendarData() {
        List<BackupFileInfo> backups = backupService.listBackups();
        
        // Group backups by date and calculate totals
        Map<String, List<BackupFileInfo>> byDate = backups.stream()
            .collect(Collectors.groupingBy(b -> b.createdAt().toLocalDate().toString()));
        
        return byDate.entrySet().stream()
            .map(entry -> {
                int count = entry.getValue().size();
                double totalSizeMb = entry.getValue().stream()
                    .mapToDouble(BackupFileInfo::sizeMb)
                    .sum();
                return new CalendarEvent(entry.getKey(), count, totalSizeMb);
            })
            .sorted(Comparator.comparing(CalendarEvent::date))
            .collect(Collectors.toList());
    }
    
    /**
     * API endpoint for chart data (size over time)
     */
    @GetMapping("/api/chart")
    @ResponseBody
    public ChartData getChartData() {
        List<BackupFileInfo> backups = backupService.listBackups();
        
        // Sort by date ascending
        List<BackupFileInfo> sortedBackups = backups.stream()
            .sorted(Comparator.comparing(BackupFileInfo::createdAt))
            .toList();
        
        List<String> labels = sortedBackups.stream()
            .map(b -> b.createdAt().toLocalDate().toString())
            .toList();
            
        List<Double> sizes = sortedBackups.stream()
            .map(BackupFileInfo::sizeMb)
            .toList();
            
        return new ChartData(labels, sizes);
    }
    
    /**
     * API endpoint for disk space
     */
    @GetMapping("/api/disk-space")
    @ResponseBody
    public DiskSpaceInfo getDiskSpaceApi() {
        return getDiskSpaceInfo();
    }
    
    // ===================== Private Methods =====================
    
    private BackupStats calculateStats(List<BackupFileInfo> backups) {
        if (backups.isEmpty()) {
            return new BackupStats(0, 0, 0, 0, 0);
        }
        
        double totalSizeMb = backups.stream().mapToDouble(BackupFileInfo::sizeMb).sum();
        double avgSizeMb = totalSizeMb / backups.size();
        double maxSizeMb = backups.stream().mapToDouble(BackupFileInfo::sizeMb).max().orElse(0);
        double minSizeMb = backups.stream().mapToDouble(BackupFileInfo::sizeMb).min().orElse(0);
        
        return new BackupStats(backups.size(), totalSizeMb, avgSizeMb, maxSizeMb, minSizeMb);
    }
    
    private DiskSpaceInfo getDiskSpaceInfo() {
        try {
            String backupDir = backupService.getBackupDirectory();
            File dir = new File(backupDir);
            
            // Make sure parent exists
            if (!dir.exists()) {
                dir = dir.getParentFile();
                if (dir == null || !dir.exists()) {
                    dir = new File(System.getProperty("user.home"));
                }
            }
            
            FileStore store = Files.getFileStore(dir.toPath());
            
            long totalSpace = store.getTotalSpace();
            long usableSpace = store.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;
            
            double totalGb = totalSpace / (1024.0 * 1024.0 * 1024.0);
            double usableGb = usableSpace / (1024.0 * 1024.0 * 1024.0);
            double usedGb = usedSpace / (1024.0 * 1024.0 * 1024.0);
            double usedPercent = (usedSpace * 100.0) / totalSpace;
            
            boolean isLow = usableGb < LOW_DISK_SPACE_THRESHOLD_GB;
            
            return new DiskSpaceInfo(totalGb, usableGb, usedGb, usedPercent, isLow);
            
        } catch (Exception e) {
            log.error("Error getting disk space: {}", e.getMessage());
            return new DiskSpaceInfo(0, 0, 0, 0, false);
        }
    }
    
    private long calculateDaysSinceLastBackup(BackupConfiguration config) {
        if (config.getLastBackupDate() == null) {
            return -1; // No backup ever
        }

        // last_backup_date is stored in UTC (JVM timezone).
        // Convert to local (America/Mexico_City) before comparing with today's local date,
        // otherwise an evening backup (e.g. 18:49 CDT = 00:49+1d UTC) returns -1
        // and the UI shows "Nunca se ha realizado un respaldo".
        LocalDate lastBackupLocalDate = dateTimeService.toCompanyTime(config.getLastBackupDate()).toLocalDate();
        return ChronoUnit.DAYS.between(
            lastBackupLocalDate,
            dateTimeService.todayLocal()
        );
    }
    
    // ===================== Record Classes =====================
    
    public record BackupStats(
        int totalCount,
        double totalSizeMb,
        double avgSizeMb,
        double maxSizeMb,
        double minSizeMb
    ) {}
    
    public record DiskSpaceInfo(
        double totalGb,
        double freeGb,
        double usedGb,
        double usedPercent,
        boolean isLow
    ) {}
    
    public record CalendarEvent(
        String date,
        int count,
        double totalSizeMb
    ) {}
    
    public record ChartData(
        List<String> labels,
        List<Double> sizes
    ) {}
}
