package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class BackupApplicationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DatabaseDomainService domainService;
    private final JdbcTemplate jdbcTemplate;

    public BackupApplicationService(DatabaseDomainService domainService, JdbcTemplate jdbcTemplate) {
        this.domainService = domainService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> createBackup(String databaseName) {
        domainService.validateDatabaseName(databaseName);
        Path backupDir = backupDir(databaseName);
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new IllegalStateException("创建备份目录失败", e);
        }
        String fileName = databaseName + "-" + LocalDateTime.now().format(FORMATTER) + ".sql";
        Path file = backupDir.resolve(fileName);
        jdbcTemplate.execute("SCRIPT TO '" + escapeSqlPath(file.toAbsolutePath().toString()) + "'");
        return Map.of("name", fileName, "path", file.toAbsolutePath().toString());
    }

    public List<Map<String, Object>> listBackups(String databaseName) {
        domainService.validateDatabaseName(databaseName);
        Path backupDir = backupDir(databaseName);
        if (!Files.exists(backupDir)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> backups = new ArrayList<>();
            try (var stream = Files.list(backupDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                        .sorted(Comparator.comparing(this::safeLastModified).reversed())
                        .forEach(path -> backups.add(Map.of(
                                "name", path.getFileName().toString(),
                                "size", safeSize(path),
                                "updatedAt", safeModifiedAt(path)
                        )));
            }
            return backups;
        } catch (IOException e) {
            throw new IllegalStateException("读取备份列表失败", e);
        }
    }

    public void restoreBackup(String databaseName, String backupName) {
        domainService.validateDatabaseName(databaseName);
        Path file = resolveBackupFile(databaseName, backupName);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("备份文件不存在");
        }
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("RUNSCRIPT FROM '" + escapeSqlPath(file.toAbsolutePath().toString()) + "'");
    }

    public void deleteBackup(String databaseName, String backupName) {
        domainService.validateDatabaseName(databaseName);
        Path file = resolveBackupFile(databaseName, backupName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("删除备份文件失败", e);
        }
    }

    private Path backupDir(String databaseName) {
        return Path.of("data", "backups", databaseName);
    }

    private Path resolveBackupFile(String databaseName, String backupName) {
        if (backupName == null || backupName.isBlank()) {
            throw new IllegalArgumentException("备份文件名不能为空");
        }
        Path backupDir = backupDir(databaseName).toAbsolutePath().normalize();
        Path file = backupDir.resolve(backupName).normalize();
        if (!file.startsWith(backupDir)) {
            throw new IllegalArgumentException("备份文件路径非法");
        }
        return file;
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    private String safeModifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toString();
        } catch (IOException e) {
            return "";
        }
    }

    private long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private String escapeSqlPath(String path) {
        return path.replace("\\", "/").replace("'", "''");
    }
}