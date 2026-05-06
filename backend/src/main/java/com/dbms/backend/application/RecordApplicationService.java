package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.RecordGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Application service for record CRUD operations.
 */
/**
 * 记录应用服务，提供对数据库表中记录的增、删、改、查操作。
 * <p>
 * 通过协调领域层服务（{@link DatabaseDomainService}）和
 * 基础设施层网关（{@link RecordGateway}）来完成数据记录的管理。
 * </p>
 *
 * @author DBMS Team
 */
@Service
public class RecordApplicationService {

    /** 数据库领域服务，用于校验数据库名称和表名 */
    private final DatabaseDomainService domainService;

    /** 记录网关，负责实际的记录读写操作 */
    private final RecordGateway recordGateway;

    /**
     * 构造方法。
     *
     * @param domainService 数据库领域服务
     * @param recordGateway 记录网关
     */
    public RecordApplicationService(DatabaseDomainService domainService, RecordGateway recordGateway) {
        this.domainService = domainService;
        this.recordGateway = recordGateway;
    }

    /**
     * 向指定数据库的表中插入一条记录。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param values       字段名与值的映射
     * @return 受影响的行数
     */
    public int insert(String databaseName, String tableName, Map<String, Object> values) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return recordGateway.insert(databaseName, tableName, values);
    }

    /**
     * 查询指定数据库的表中符合条件的记录。
     * <p>
     * 查询结果有分页限制，默认每页 50 条，最多 200 条。
     * </p>
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param filters      筛选条件（字段名与值的映射）
     * @param limit        每页记录数（默认为 50，范围 1~200）
     * @param offset       偏移量（默认为 0）
     * @return 符合条件的记录列表
     */
    public List<Map<String, Object>> query(String databaseName, String tableName,
                                           Map<String, Object> filters, Integer limit, Integer offset) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");

        int safeLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
        int safeOffset = offset == null ? 0 : Math.max(offset, 0);
        return recordGateway.query(databaseName, tableName, filters, safeLimit, safeOffset);
    }

    /**
     * 更新指定数据库的表中符合条件的记录。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param filters      筛选条件
     * @param values       待更新的字段与值
     * @return 受影响的行数
     */
    public int update(String databaseName, String tableName,
                      Map<String, Object> filters, Map<String, Object> values) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return recordGateway.update(databaseName, tableName, filters, values);
    }

    /**
     * 删除指定数据库的表中符合条件的记录。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param filters      筛选条件
     * @return 受影响的行数
     */
    public int delete(String databaseName, String tableName, Map<String, Object> filters) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        
        // 安全防护：如果filters为空，不允许删除所有记录
        if (filters == null || filters.isEmpty()) {
            throw new IllegalArgumentException("删除操作必须指定过滤条件，不允许删除所有记录");
        }
        
        return recordGateway.delete(databaseName, tableName, filters);
    }
}