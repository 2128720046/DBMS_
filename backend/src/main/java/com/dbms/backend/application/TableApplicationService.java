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
                // Normalize identifiers first so H2 metadata and later lookups use the same schema/table form.
            String normalizedDb = domainService.normalizeDatabaseName(databaseName);
            String normalizedTable = domainService.normalizeIdentifier(tableName);
            tableGateway.createTable(normalizedDb, normalizedTable, columns);
    }

    public void updateTableStructure(String databaseName, String tableName, List<ColumnDefinition> columns) {
                // Structure changes must target the same canonical object names as the original create call.
            String normalizedDb = domainService.normalizeDatabaseName(databaseName);
            String normalizedTable = domainService.normalizeIdentifier(tableName);
            tableGateway.alterTableStructure(normalizedDb, normalizedTable, columns);
    }

    public void dropTable(String databaseName, String tableName) {
                // Drop the canonical identifier so case mismatches do not leave orphan metadata behind.
            String normalizedDb = domainService.normalizeDatabaseName(databaseName);
            String normalizedTable = domainService.normalizeIdentifier(tableName);
            tableGateway.dropTable(normalizedDb, normalizedTable);
    }

    public List<TableInfo> listTables(String databaseName) {
            String normalizedDb = domainService.normalizeDatabaseName(databaseName);
            return tableGateway.listTables(normalizedDb).stream().map(TableInfo::new).toList();
    }

    public Map<String, Object> getTableDetail(String databaseName, String tableName) {
            String normalizedDb = domainService.normalizeDatabaseName(databaseName);
            String normalizedTable = domainService.normalizeIdentifier(tableName);
            return tableGateway.getTableDetail(normalizedDb, normalizedTable);
    }
}
