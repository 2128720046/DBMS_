package com.dbms.backend.modules.log.infrastructure;

import com.dbms.backend.core.storage.config.StorageEngineConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 请求日志记录器。
 * <p>
 * 每次 Spring Boot 启动时创建一个以启动时间命名的日志文件（如 {@code 20250510_143022.log}），
 * 写入 data/ 目录。所有前端 SQL 请求及其执行结果均追加写入该文件。
 * </p>
 */
@Component
public class RequestLogger {

    private static final Logger log = LoggerFactory.getLogger(RequestLogger.class);

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final DateTimeFormatter LOG_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 日志文件写入器 */
    private PrintWriter writer;

    /** 日志文件路径（供调试观察） */
    private String logFilePath;

    /**
     * 初始化日志文件：在 data/ 目录下创建以启动时间命名的 .log 文件。
     */
    @PostConstruct
    public void init() {
        try {
            String dataDir = StorageEngineConfig.getDATA_DIR();
            File dir = new File(dataDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String startTime = LocalDateTime.now().format(FILE_TIMESTAMP);
            String fileName = startTime + ".log";
            File logFile = new File(dir, fileName);
            this.logFilePath = logFile.getAbsolutePath();

            // 追加模式（每次启动新文件，但如果同一秒多次启动也不覆盖）
            this.writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile, true), StandardCharsets.UTF_8), true);

            log.info("请求日志文件已初始化: {}", logFilePath);
            writeSeparator("服务启动");
        } catch (IOException e) {
            log.error("初始化请求日志文件失败: {}", e.getMessage());
        }
    }

    /**
     * 记录一条请求与响应日志。
     *
     * @param databaseName 数据库名称
     * @param sql          原始 SQL 语句
     * @param success      是否执行成功
     * @param resultType   结果类型（message / table / error）
     * @param affectedRows 影响行数
     * @param elapsedMs    执行耗时（毫秒）
     * @param errorMessage 错误信息（成功时可为空）
     */
    public synchronized void log(String databaseName, String sql,
                                  boolean success, String resultType,
                                  int affectedRows, long elapsedMs,
                                  String errorMessage) {
        if (writer == null) {
            return;
        }

        String now = LocalDateTime.now().format(LOG_TIMESTAMP);
        String db = (databaseName == null || databaseName.isBlank()) ? "(无)" : databaseName;
        String status = success ? "成功" : "失败";

        writer.printf("[%s] [%s] [%s] 数据库=%s | 影响行数=%d | 耗时=%dms%n",
                now, status, resultType, db, affectedRows, elapsedMs);
        writer.printf("  SQL: %s%n", sanitize(sql));
        if (errorMessage != null && !errorMessage.isBlank()) {
            writer.printf("  错误: %s%n", sanitize(errorMessage));
        }
        writer.println("---");
    }

    /**
     * 记录一条纯信息日志（如服务启动、关闭等）。
     */
    public synchronized void writeSeparator(String message) {
        if (writer == null) {
            return;
        }
        String now = LocalDateTime.now().format(LOG_TIMESTAMP);
        writer.printf("%n========== [%s] %s ==========%n%n", now, message);
    }

    /**
     * 关闭日志文件写入器。
     */
    @PreDestroy
    public void destroy() {
        if (writer != null) {
            writeSeparator("服务关闭");
            writer.close();
            log.info("请求日志文件已关闭: {}", logFilePath);
        }
    }

    /**
     * 清洗字符串中的不可见控制字符（保留换行和缩进的可读格式）。
     */
    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
    }
}
