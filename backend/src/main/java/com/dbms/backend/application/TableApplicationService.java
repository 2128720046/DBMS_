package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.TableGateway;
import com.dbms.backend.dto.ColumnDefinition;
import com.dbms.backend.model.TableInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TableApplicationService {

    private final DatabaseDomainService domainService;
    private final TableGateway tableGateway;

    public TableApplicationService(DatabaseDomainService domainService, TableGateway tableGateway) {
        this.domainService = domainService;
        this.tableGateway = tableGateway;
    }

    public void createTable(String databaseName, String tableName, List<ColumnDefinition> columns) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        tableGateway.createTable(databaseName, tableName, columns);
    }

    public void dropTable(String databaseName, String tableName) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        tableGateway.dropTable(databaseName, tableName);
    }

    public List<TableInfo> listTables(String databaseName) {
        domainService.validateDatabaseName(databaseName);
        return tableGateway.listTables(databaseName).stream().map(TableInfo::new).toList();
    }

    public Map<String, Object> getTableDetail(String databaseName, String tableName) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return tableGateway.getTableDetail(databaseName, tableName);
    }
}
