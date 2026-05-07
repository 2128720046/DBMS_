package com.dbms.backend.modules.integrity.infrastructure;

import com.dbms.backend.modules.integrity.domain.IntegrityGateway;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 完整性约束网关占位实现。
 */
@Repository
public class TodoIntegrityGatewayImpl implements IntegrityGateway {

    @Override
    public List<Map<String, Object>> listConstraints(String schemaName, String tableName) {
        return List.of();
    }

    @Override
    public void saveConstraint(String schemaName, String tableName, String constraintName,
                               String columnName, String type, String parameter) {
        throw new UnsupportedOperationException("约束保存功能尚未实现，请在 TodoIntegrityGatewayImpl#saveConstraint 中补充代码");
    }

    @Override
    public void dropConstraint(String schemaName, String tableName, String constraintName) {
        throw new UnsupportedOperationException("约束删除功能尚未实现，请在 TodoIntegrityGatewayImpl#dropConstraint 中补充代码");
    }

    @Override
    public List<Map<String, Object>> validateRow(String schemaName, String tableName, Map<String, Object> row) {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> validateTable(String schemaName, String tableName) {
        return List.of();
    }
}
