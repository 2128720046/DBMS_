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

/**
 * 备份应用服务，负责数据库的备份与恢复操作。
 * <p>
 * 提供创建备份、列出备份、恢复备份和删除备份的功能。
 * 备份文件以 SQL 脚本格式存储在文件系统中
 * </p>
 *
 * @author DBMS Team
 */
@Service
public class BackupApplicationService {

    /** 备份文件名中的日期时间格式化器，格式：yyyyMMddHHmmss */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 数据库领域服务，用于校验数据库名称等 */
    private final DatabaseDomainService domainService;

    /** Spring JDBC 模板，用于执行 SQL 脚本 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造方法。
     *
     * @param domainService 数据库领域服务
     * @param jdbcTemplate  Spring JDBC 模板
     */
    public BackupApplicationService(DatabaseDomainService domainService, JdbcTemplate jdbcTemplate) {
        this.domainService = domainService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建数据库备份。
     * <p>
     * 在 data/backups/{databaseName}/ 目录下生成以时间戳命名的 .sql 备份文件。
     * </p>
     *
     * @param databaseName 数据库名称
     * @return 包含备份文件名和路径的 Map
     */
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

    /**
     * 列出指定数据库的所有备份文件。
     * <p>
     * 按最后修改时间倒序排列。
     * </p>
     *
     * @param databaseName 数据库名称
     * @return 备份文件列表，每个元素包含名称、大小和更新时间
     */
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

    /**
     * 恢复指定数据库的备份。
     * <p>
     * 先删除当前所有对象，然后执行备份 SQL 脚本恢复数据。
     * </p>
     *
     * @param databaseName 数据库名称
     * @param backupName   备份文件名
     */
    public void restoreBackup(String databaseName, String backupName) {
        domainService.validateDatabaseName(databaseName);
        Path file = resolveBackupFile(databaseName, backupName);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("备份文件不存在");
        }
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("RUNSCRIPT FROM '" + escapeSqlPath(file.toAbsolutePath().toString()) + "'");
    }

    /**
     * 删除指定数据库的某个备份文件。
     *
     * @param databaseName 数据库名称
     * @param backupName   备份文件名
     */
    public void deleteBackup(String databaseName, String backupName) {
        domainService.validateDatabaseName(databaseName);
        Path file = resolveBackupFile(databaseName, backupName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("删除备份文件失败", e);
        }
    }

    /**
     * 获取备份文件存储目录。
     *
     * @param databaseName 数据库名称
     * @return 备份目录路径
     */
    private Path backupDir(String databaseName) {
        return Path.of("data", "backups", databaseName);
    }

    /**
     * 解析备份文件完整路径，同时防止路径穿越攻击。
     *
     * @param databaseName 数据库名称
     * @param backupName   备份文件名
     * @return 解析后的文件路径
     * @throws IllegalArgumentException 如果备份文件名为空或路径非法
     */
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

    /**
     * 安全获取文件大小，失败时返回 -1。
     */
    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    /**
     * 安全获取文件修改时间，失败时返回空字符串。
     */
    private String safeModifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toString();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 安全获取文件最后修改时间戳（毫秒），失败时返回 0。
     */
    private long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 转义 SQL 脚本路径中的特殊字符。
     * <p>
     * 将 Windows 反斜杠替换为正斜杠，并将单引号替换为两个单引号。
     * </p>
     *
     * @param path 原始文件路径
     * @return 转义后的路径字符串
     */
    private String escapeSqlPath(String path) {
        return path.replace("\\", "/").replace("'", "''");
    }
}