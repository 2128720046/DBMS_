package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import com.dbms.backend.model.DatabaseInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据库用例服务：创建/删除/列出数据库，封装名称校验与网关调用。
 */
@Service
public class DatabaseApplicationService {

    /** 数据库领域服务，负责数据库名称校验和规范化 */
    private final DatabaseDomainService domainService;

    /** 数据库模式网关，负责实际的数据库模式创建/删除操作 */
    private final DatabaseSchemaGateway schemaGateway;

    /**
     * 构造方法。
     *
     * @param domainService 数据库领域服务
     * @param schemaGateway 数据库模式网关
     */
    public DatabaseApplicationService(DatabaseDomainService domainService, DatabaseSchemaGateway schemaGateway) {
        this.domainService = domainService;
        this.schemaGateway = schemaGateway;
    }

    /**
     * 创建数据库。
     *
     * @param dbName 数据库名称
     */
    public void createDatabase(String dbName) {
        String normalizedDbName = domainService.normalizeDatabaseName(dbName);
        schemaGateway.createSchema(normalizedDbName);
    }

    /**
     * 删除数据库。
     *
     * @param dbName 数据库名称
     */
    public void dropDatabase(String dbName) {
        String normalizedDbName = domainService.normalizeDatabaseName(dbName);
        schemaGateway.dropSchema(normalizedDbName);
    }

    /**
     * 获取所有数据库列表。
     *
     * @return 数据库信息列表
     */
    public List<DatabaseInfo> listDatabases() {
        return schemaGateway.listSchemas().stream().map(DatabaseInfo::new).toList();
    }
    
}