package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.BackupConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.BackupConfigurationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of BackupService for MySQL database backups
 */
@Service
@Slf4j
public class BackupServiceImpl implements BackupService {

    private final BackupConfigurationRepository configRepository;
    private final BackupNotificationService notificationService;
    
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    
    @Value("${spring.datasource.username}")
    private String dbUsername;
    
    @Value("${spring.datasource.password}")
    private String dbPassword;
    
    // Flag to prevent concurrent backups
    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);
    
    // Flag to track if current backup is automatic
    private final AtomicBoolean isAutomaticBackup = new AtomicBoolean(false);
    
    // Default backup directory — must match the Docker volume mount in docker-compose.yml
    // Volume: backup_data:/root/elgransazon_backups
    private static final String DEFAULT_BACKUP_DIR = "elgransazon_backups";
    
    public BackupServiceImpl(BackupConfigurationRepository configRepository, 
                            BackupNotificationService notificationService) {
        this.configRepository = configRepository;
        this.notificationService = notificationService;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<BackupConfiguration> getConfiguration() {
        // GLOBAL configuration - only ONE record exists
        // Auto-create if not exists (lazy initialization)
        return configRepository.findAll().stream().findFirst();
    }
    
    @Override
    @Transactional
    public BackupConfiguration getOrCreateGlobalConfiguration() {
        // Get or create the single global backup configuration
        return configRepository.findAll().stream().findFirst()
            .orElseGet(() -> {
                log.info("Creating default global backup configuration...");
                BackupConfiguration config = BackupConfiguration.builder()
                    .enabled(false)
                    .frequencyDays(7)
                    .backupTime(LocalTime.of(2, 0))
                    .retentionCount(10)
                    .backupPath("backups")
                    .build();
                return configRepository.save(config);
            });
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BackupConfiguration> getAllConfigurations() {
        // For backwards compatibility - returns single global config as list
        return configRepository.findAll();
    }
    
    @Override
    @Transactional
    public BackupConfiguration saveConfiguration(BackupConfiguration config) {
        // Save the configuration directly (it already has an ID and company)
        config.setUpdatedAt(LocalDateTime.now());
        return configRepository.save(config);
    }
    
    @Override
    @Transactional
    public Optional<String> createManualBackup() {
        if (!backupInProgress.compareAndSet(false, true)) {
            log.warn("Backup already in progress, skipping manual backup request");
            return Optional.empty();
        }
        
        try {
            isAutomaticBackup.set(false);
            log.info("Starting manual database backup...");
            notificationService.notifyBackupStarted(false);
            Optional<String> result = performBackup();
            
            // Auto-clean old backups to maintain retention limit
            if (result.isPresent()) {
                cleanOldBackups();
            }
            
            return result;
        } finally {
            backupInProgress.set(false);
        }
    }
    
    @Override
    @Scheduled(cron = "0 * * * * ?") // Check every minute
    @Transactional
    public void createScheduledBackup() {
        // GLOBAL: Get the single backup configuration
        Optional<BackupConfiguration> configOpt = getConfiguration();
        
        if (configOpt.isEmpty()) {
            // No configuration exists yet
            return;
        }
        
        BackupConfiguration config = configOpt.get();
        
        if (!config.getEnabled()) {
            // Backup is disabled
            return;
        }
        
        LocalTime now = LocalTime.now();
        java.time.LocalDate today = java.time.LocalDate.now();
        LocalTime backupTime = config.getBackupTime();
        
        // Check if it's time to run backup (same hour and minute)
        if (now.getHour() != backupTime.getHour() || now.getMinute() != backupTime.getMinute()) {
            return;
        }
        
        // Check if we should run backup based on frequency
        if (config.getLastBackupDate() != null) {
            LocalDateTime lastBackup = config.getLastBackupDate();
            java.time.LocalDate lastBackupDate = lastBackup.toLocalDate();
            
            // For daily backups (frequency = 1), only skip if backup was done TODAY
            if (config.getFrequencyDays() == 1) {
                if (lastBackupDate.equals(today) && 
                    lastBackup.toLocalTime().isAfter(backupTime.minusMinutes(1))) {
                    log.debug("Scheduled backup already done today at {}", lastBackup.toLocalTime());
                    return;
                }
            } else {
                // For other frequencies, check days since last backup
                long daysSinceLastBackup = java.time.temporal.ChronoUnit.DAYS.between(
                    lastBackupDate, today);
                
                if (daysSinceLastBackup < config.getFrequencyDays()) {
                    return;
                }
            }
        }
        
        if (!backupInProgress.compareAndSet(false, true)) {
            log.warn("Backup already in progress, skipping scheduled backup");
            return;
        }
        
        try {
            isAutomaticBackup.set(true);
            log.info("Starting scheduled database backup...");
            notificationService.notifyBackupStarted(true);
            performBackup();
            cleanOldBackups();
        } finally {
            backupInProgress.set(false);
        }
    }
    
    @Override
    public List<BackupFileInfo> listBackups() {
        Path backupDir = Paths.get(getBackupDirectory());
        
        if (!Files.exists(backupDir)) {
            return Collections.emptyList();
        }
        
        try {
            return Files.list(backupDir)
                .filter(path -> path.toString().endsWith(".sql") || path.toString().endsWith(".sql.gz"))
                .map(Path::toFile)
                .sorted((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified())) // Newest first
                .map(BackupFileInfo::fromFile)
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error listing backup files: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public boolean deleteBackup(String filename) {
        // Security: prevent directory traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("Invalid backup filename attempted: {}", filename);
            return false;
        }
        
        Path backupFile = Paths.get(getBackupDirectory(), filename);
        
        try {
            if (Files.exists(backupFile)) {
                Files.delete(backupFile);
                log.info("Deleted backup file: {}", filename);
                return true;
            }
        } catch (IOException e) {
            log.error("Error deleting backup file {}: {}", filename, e.getMessage());
        }
        
        return false;
    }
    
    @Override
    @Transactional
    public void cleanOldBackups() {
        // Get retention count from first available config, or use default
        int retentionCount = getConfiguration()
            .map(BackupConfiguration::getRetentionCount)
            .orElse(10);
            
        List<BackupFileInfo> backups = listBackups();
        
        if (backups.size() <= retentionCount) {
            return;
        }
        
        // Delete oldest backups exceeding retention count
        List<BackupFileInfo> toDelete = backups.subList(retentionCount, backups.size());
        
        for (BackupFileInfo backup : toDelete) {
            deleteBackup(backup.filename());
        }
        
        log.info("Cleaned {} old backup(s), keeping {} most recent", toDelete.size(), retentionCount);
    }
    
    @Override
    public String getBackupDirectory() {
        Optional<BackupConfiguration> configOpt = getConfiguration();
        String customPath = configOpt.map(BackupConfiguration::getBackupPath).orElse(null);

        if (customPath != null && !customPath.isBlank()) {
            Path path = Paths.get(customPath);
            if (path.isAbsolute()) {
                return customPath;
            }
            // Relative path: always resolve relative to user.home so it stays
            // inside the Docker volume mounted at /root/elgransazon_backups.
            // A custom relative value like "backups" resolves to /root/backups
            // which would be outside the volume — so we ignore relative custom
            // paths and fall through to the default.
        }

        // Default: /root/elgransazon_backups (matches Docker volume mount)
        return Paths.get(System.getProperty("user.home"), DEFAULT_BACKUP_DIR).toString();
    }
    
    @Override
    public Optional<File> getBackupFile(String filename) {
        // Security: prevent directory traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("Invalid backup filename requested: {}", filename);
            return Optional.empty();
        }
        
        File file = Paths.get(getBackupDirectory(), filename).toFile();
        
        if (file.exists() && file.isFile()) {
            return Optional.of(file);
        }
        
        return Optional.empty();
    }
    
    @Override
    public boolean isBackupInProgress() {
        return backupInProgress.get();
    }
    
    // ===================== Private Methods =====================

    /**
     * Detects whether the given mysqldump binary is MariaDB or MySQL.
     * MariaDB client (installed via Alpine apk mysql-client) reports "mariadb"
     * in its --version output and uses --skip-ssl.
     * The official MySQL client uses --ssl-mode=DISABLED.
     */
    private String detectSslFlag(String mysqldumpPath) {
        try {
            Process p = new ProcessBuilder(mysqldumpPath, "--version")
                    .redirectErrorStream(true)
                    .start();
            String version = new String(p.getInputStream().readAllBytes()).toLowerCase();
            p.waitFor();
            boolean isMariaDb = version.contains("mariadb");
            log.debug("mysqldump client: {}, ssl flag: {}",
                    isMariaDb ? "MariaDB" : "MySQL",
                    isMariaDb ? "--skip-ssl" : "--ssl-mode=DISABLED");
            return isMariaDb ? "--skip-ssl" : "--ssl-mode=DISABLED";
        } catch (Exception e) {
            log.warn("Could not detect mysqldump client type, defaulting to --ssl-mode=DISABLED");
            return "--ssl-mode=DISABLED";
        }
    }

    /**
     * Find mysqldump executable in common installation paths
     */
    private String findMysqldumpPath() {
        // First try if mysqldump is in PATH
        try {
            ProcessBuilder testPb = new ProcessBuilder("mysqldump", "--version");
            Process testProcess = testPb.start();
            int exitCode = testProcess.waitFor();
            if (exitCode == 0) {
                return "mysqldump";
            }
        } catch (Exception ignored) {
            // Not in PATH, continue searching
        }
        
        // Common MySQL installation paths on Windows
        List<String> commonPaths = new ArrayList<>();
        
        // MySQL Server installations
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        
        if (programFiles != null) {
            // Check various MySQL versions
            for (int major = 8; major >= 5; major--) {
                for (int minor = 4; minor >= 0; minor--) {
                    commonPaths.add(programFiles + "\\MySQL\\MySQL Server " + major + "." + minor + "\\bin\\mysqldump.exe");
                }
            }
            commonPaths.add(programFiles + "\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe");
        }
        
        if (programFilesX86 != null) {
            commonPaths.add(programFilesX86 + "\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe");
        }
        
        // XAMPP
        commonPaths.add("C:\\xampp\\mysql\\bin\\mysqldump.exe");
        
        // WAMP
        commonPaths.add("C:\\wamp64\\bin\\mysql\\mysql8.0.31\\bin\\mysqldump.exe");
        commonPaths.add("C:\\wamp64\\bin\\mysql\\mysql8.0.21\\bin\\mysqldump.exe");
        commonPaths.add("C:\\wamp\\bin\\mysql\\mysql8.0.31\\bin\\mysqldump.exe");
        
        // MAMP
        commonPaths.add("C:\\MAMP\\bin\\mysql\\bin\\mysqldump.exe");
        
        // Laragon
        commonPaths.add("C:\\laragon\\bin\\mysql\\mysql-8.0.30-winx64\\bin\\mysqldump.exe");
        commonPaths.add("C:\\laragon\\bin\\mysql\\mysql-5.7.24-winx64\\bin\\mysqldump.exe");
        
        // MariaDB
        commonPaths.add(programFiles + "\\MariaDB 10.11\\bin\\mysqldump.exe");
        commonPaths.add(programFiles + "\\MariaDB 10.6\\bin\\mysqldump.exe");
        
        // Check each path
        for (String path : commonPaths) {
            File file = new File(path);
            if (file.exists() && file.canExecute()) {
                log.info("Found mysqldump at: {}", path);
                return path;
            }
        }
        
        // Also try to find in MySQL bin folders dynamically
        String[] searchDirs = {
            programFiles + "\\MySQL",
            "C:\\xampp\\mysql\\bin",
            "C:\\wamp64\\bin\\mysql",
            "C:\\laragon\\bin\\mysql"
        };
        
        for (String dir : searchDirs) {
            if (dir == null) continue;
            File searchDir = new File(dir);
            if (searchDir.exists() && searchDir.isDirectory()) {
                File found = findMysqldumpRecursive(searchDir, 3);
                if (found != null) {
                    log.info("Found mysqldump at: {}", found.getAbsolutePath());
                    return found.getAbsolutePath();
                }
            }
        }
        
        log.error("mysqldump not found in any common location. Please add MySQL bin directory to PATH.");
        return null;
    }
    
    private File findMysqldumpRecursive(File dir, int maxDepth) {
        if (maxDepth <= 0 || !dir.isDirectory()) return null;
        
        File[] files = dir.listFiles();
        if (files == null) return null;
        
        for (File file : files) {
            if (file.isFile() && file.getName().equalsIgnoreCase("mysqldump.exe")) {
                return file;
            }
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findMysqldumpRecursive(file, maxDepth - 1);
                if (found != null) return found;
            }
        }
        
        return null;
    }
    
    private Optional<String> performBackup() {
        String backupDir = getBackupDirectory();
        
        // Ensure backup directory exists
        try {
            Files.createDirectories(Paths.get(backupDir));
        } catch (IOException e) {
            log.error("Failed to create backup directory: {}", e.getMessage());
            updateBackupStatus(null, "FAILED", 0);
            return Optional.empty();
        }
        
        // Parse database name from URL
        String dbName = extractDatabaseName();
        if (dbName == null) {
            log.error("Could not extract database name from datasource URL");
            updateBackupStatus(null, "FAILED", 0);
            return Optional.empty();
        }
        
        // Generate filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        String filename = String.format("backup_%s_%s.sql", dbName, timestamp);
        String compressedFilename = filename + ".gz";
        
        Path tempFile = Paths.get(backupDir, filename);
        Path compressedFile = Paths.get(backupDir, compressedFilename);
        
        try {
            // Find mysqldump executable
            String mysqldumpPath = findMysqldumpPath();
            if (mysqldumpPath == null) {
                log.error("mysqldump executable not found. Please install MySQL or add it to PATH.");
                updateBackupStatus(null, "FAILED - mysqldump no encontrado", 0);
                return Optional.empty();
            }
            
            // Execute mysqldump
            String host = extractHost();
            String port = extractPort();
            
            List<String> command = new ArrayList<>();
            command.add(mysqldumpPath);
            command.add("--host=" + host);
            command.add("--port=" + port);
            command.add("--user=" + dbUsername);
            if (dbPassword != null && !dbPassword.isEmpty()) {
                command.add("--password=" + dbPassword);
            }
            // Disable SSL: both containers share the same private Docker network
            // so encryption is unnecessary. The flag differs by client:
            //   MariaDB client (Alpine apk mysql-client) → --skip-ssl
            //   MySQL client (Windows / official MySQL) → --ssl-mode=DISABLED
            command.add(detectSslFlag(mysqldumpPath));
            command.add("--single-transaction");
            command.add("--routines");
            command.add("--triggers");
            command.add("--result-file=" + tempFile.toString());
            command.add(dbName);
            
            log.info("Executing mysqldump for database: {}", dbName);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output for debugging
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.error("mysqldump failed with exit code {}: {}", exitCode, output);
                Files.deleteIfExists(tempFile);
                updateBackupStatus(null, "FAILED", 0);
                notificationService.notifyBackupFailed("mysqldump falló con código " + exitCode);
                return Optional.empty();
            }
            
            // Compress the backup
            notificationService.notifyBackupProgress("Comprimiendo respaldo...");
            compressFile(tempFile, compressedFile);
            
            // Delete uncompressed file
            Files.deleteIfExists(tempFile);
            
            // Get file size
            double sizeMb = Files.size(compressedFile) / (1024.0 * 1024.0);
            
            // Update configuration with last backup info
            updateBackupStatus(compressedFilename, "SUCCESS", sizeMb);
            
            log.info("Backup completed successfully: {} ({:.2f} MB)", compressedFilename, sizeMb);
            
            // Send success notification
            notificationService.notifyBackupCompleted(compressedFilename, sizeMb);
            
            return Optional.of(compressedFilename);
            
        } catch (Exception e) {
            log.error("Backup failed: {}", e.getMessage(), e);
            try {
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(compressedFile);
            } catch (IOException ignored) {}
            updateBackupStatus(null, "FAILED", 0);
            notificationService.notifyBackupFailed(e.getMessage());
            return Optional.empty();
        }
    }
    
    private void compressFile(Path source, Path target) throws IOException {
        try (InputStream fis = Files.newInputStream(source);
             OutputStream fos = Files.newOutputStream(target);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                gzos.write(buffer, 0, len);
            }
        }
    }
    
    @Transactional
    protected void updateBackupStatus(String filename, String status, double sizeMb) {
        Optional<BackupConfiguration> configOpt = getConfiguration();
        if (configOpt.isEmpty()) {
            log.warn("Cannot update backup status - no configuration found");
            return;
        }
        BackupConfiguration config = configOpt.get();
        config.setLastBackupDate(LocalDateTime.now());
        config.setLastBackupStatus(status);
        config.setLastBackupFilename(filename);
        config.setLastBackupSizeMb(sizeMb);
        configRepository.save(config);
    }
    
    private String extractDatabaseName() {
        // jdbc:mysql://localhost:3306/bd_restaurant?...
        try {
            String url = datasourceUrl;
            int startIndex = url.lastIndexOf('/') + 1;
            int endIndex = url.indexOf('?');
            if (endIndex == -1) {
                endIndex = url.length();
            }
            return url.substring(startIndex, endIndex);
        } catch (Exception e) {
            log.error("Failed to extract database name from URL: {}", datasourceUrl);
            return null;
        }
    }
    
    private String extractHost() {
        // jdbc:mysql://localhost:3306/bd_restaurant
        try {
            String url = datasourceUrl.replace("jdbc:mysql://", "");
            int colonIndex = url.indexOf(':');
            if (colonIndex > 0) {
                return url.substring(0, colonIndex);
            }
            int slashIndex = url.indexOf('/');
            return url.substring(0, slashIndex);
        } catch (Exception e) {
            return "localhost";
        }
    }
    
    private String extractPort() {
        // jdbc:mysql://localhost:3306/bd_restaurant
        try {
            String url = datasourceUrl.replace("jdbc:mysql://", "");
            int colonIndex = url.indexOf(':');
            int slashIndex = url.indexOf('/');
            if (colonIndex > 0 && slashIndex > colonIndex) {
                return url.substring(colonIndex + 1, slashIndex);
            }
        } catch (Exception e) {
            // ignored
        }
        return "3306";
    }
}
