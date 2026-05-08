package com.dbms.backend.modules.integrity.application;

import com.dbms.backend.core.naming.DatabaseDomainService;
import com.dbms.backend.modules.integrity.domain.IntegrityGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 完整性约束用例服务。
 */
@Service
public class IntegrityApplicationService {

    private final DatabaseDomainService naming;
    private final IntegrityGateway integrityGateway;

    public IntegrityApplicationService(DatabaseDomainService naming, IntegrityGateway integrityGateway) {
        this.naming = naming;
        this.integrityGateway = integrityGateway;
    }

    /**
     * 查询表约束列表。
     */
    public List<Map<String, Object>> listConstraints(String databaseName, String tableName) {
        return integrityGateway.listConstraints(naming.normalizeDatabaseName(databaseName), naming.normalizeIdentifier(tableName));
    }

    /**
     * 新增约束定义。
     */
    public void addConstraint(String databaseName, String tableName, String constraintName,
                              String columnName, String type, String parameter) {
        String normalizedDb = naming.normalizeDatabaseName(databaseName);
        String normalizedTable = naming.normalizeIdentifier(tableName);
        String normalizedConstraint = naming.normalizeIdentifier(constraintName);
        String normalizedColumn = columnName == null || columnName.isBlank() ? "" : naming.normalizeIdentifier(columnName);

        integrityGateway.saveConstraint(normalizedDb, normalizedTable, normalizedConstraint, normalizedColumn, type, parameter);

        List<Map<String, Object>> issues = integrityGateway.validateTable(normalizedDb, normalizedTable);
        if (issues != null && !issues.isEmpty()) {
            integrityGateway.dropConstraint(normalizedDb, normalizedTable, normalizedConstraint);
            throw new IllegalArgumentException("新增约束失败：现有数据违反该约束。问题示例: " + issues.get(0));
        }
    }

    /**
     * 删除约束定义。
     */
    public void dropConstraint(String databaseName, String tableName, String constraintName) {
        integrityGateway.dropConstraint(naming.normalizeDatabaseName(databaseName), naming.normalizeIdentifier(tableName),
                naming.normalizeIdentifier(constraintName));
    }

    /**
     * 执行全表约束校验。
     */
    public List<Map<String, Object>> validateTable(String databaseName, String tableName) {
        return integrityGateway.validateTable(naming.normalizeDatabaseName(databaseName), naming.normalizeIdentifier(tableName));
    }
}
