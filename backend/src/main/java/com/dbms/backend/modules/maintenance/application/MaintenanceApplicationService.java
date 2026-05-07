package com.dbms.backend.modules.maintenance.application;

import com.dbms.backend.core.naming.DatabaseDomainService;
import com.dbms.backend.modules.maintenance.domain.MaintenanceGateway;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 数据库维护用例服务。
 */
@Service
public class MaintenanceApplicationService {

    private final DatabaseDomainService naming;
    private final MaintenanceGateway maintenanceGateway;

    public MaintenanceApplicationService(DatabaseDomainService naming, MaintenanceGateway maintenanceGateway) {
        this.naming = naming;
        this.maintenanceGateway = maintenanceGateway;
    }

    public List<Map<String, Object>> listBackups(String databaseName) {
        return maintenanceGateway.listBackups(naming.normalizeDatabaseName(databaseName));
    }

    public Path backup(String databaseName, Path targetDir) {
        return maintenanceGateway.backupSchema(naming.normalizeDatabaseName(databaseName), targetDir);
    }

    public void restore(String databaseName, Path backupPath) {
        maintenanceGateway.restoreSchema(naming.normalizeDatabaseName(databaseName), backupPath);
    }

    public void deleteBackup(String databaseName, String backupName) {
        maintenanceGateway.deleteBackup(naming.normalizeDatabaseName(databaseName), backupName);
    }
}
