package com.dbms.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the DBMS backend.
 */
/**
 * DBMS后端应用程序的入口类。
 * <p>
 * 使用Spring Boot框架启动整个后端服务，负责初始化所有Bean、
 * 加载配置、启动嵌入式Web服务器等。是整个后端应用的启动点。
 * </p>
 *
 * @author DBMS Team
 */
@SpringBootApplication
public class DbmsBackendApplication {

    /**
     * 程序主入口方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DbmsBackendApplication.class, args);
    }
}

