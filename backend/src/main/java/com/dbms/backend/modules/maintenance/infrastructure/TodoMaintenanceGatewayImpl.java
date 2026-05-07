package com.dbms.backend.modules.maintenance.infrastructure;

import com.dbms.backend.modules.maintenance.domain.MaintenanceGateway;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 数据库维护网关占位实现。
 */
@Repository
public class TodoMaintenanceGatewayImpl implements MaintenanceGateway {

    @Override
    public List<Map<String, Object>> listBackups(String schemaName) {
        return List.of();
    }

    @Override
    public Path backupSchema(String schemaName, Path targetDir) {
        throw new UnsupportedOperationException("数据库备份功能尚未实现，请在 TodoMaintenanceGatewayImpl#backupSchema 中补充代码");
    }

    @Override
    public void restoreSchema(String schemaName, Path backupPath) {
        throw new UnsupportedOperationException("数据库还原功能尚未实现，请在 TodoMaintenanceGatewayImpl#restoreSchema 中补充代码");
    }

    @Override
    public void deleteBackup(String schemaName, String backupName) {
        throw new UnsupportedOperationException("备份删除功能尚未实现，请在 TodoMaintenanceGatewayImpl#deleteBackup 中补充代码");
    }
}
