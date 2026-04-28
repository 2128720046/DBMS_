package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.TableGateway;
import com.dbms.backend.dto.ColumnDefinition;
import com.dbms.backend.infrastructure.storage.RuankoBinaryCatalogService;
import com.dbms.backend.model.TableInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TableApplicationService {

    private final DatabaseDomainService domainService;
    private final TableGateway tableGateway;
    private final RuankoBinaryCatalogService binaryCatalogService;

    public TableApplicationService(DatabaseDomainService domainService,
                                   TableGateway tableGateway,
                                   RuankoBinaryCatalogService binaryCatalogService) {
        this.domainService = domainService;
        this.tableGateway = tableGateway;
        this.binaryCatalogService = binaryCatalogService;
    }

    public void createTable(String databaseName, String tableName, List<ColumnDefinition> columns) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        tableGateway.createTable(normalizedDb, tableName, columns);
        binaryCatalogService.createTableArtifacts(normalizedDb, tableName, columns);
    }

    public void updateTableStructure(String databaseName, String tableName, List<ColumnDefinition> columns) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        tableGateway.alterTableStructure(normalizedDb, tableName, columns);
        binaryCatalogService.updateTableArtifacts(normalizedDb, tableName, columns);
    }

    public void dropTable(String databaseName, String tableName) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        tableGateway.dropTable(normalizedDb, tableName);
        binaryCatalogService.dropTableArtifacts(normalizedDb, tableName);
    }

    public List<TableInfo> listTables(String databaseName) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        return tableGateway.listTables(normalizedDb).stream().map(TableInfo::new).toList();
    }

    public Map<String, Object> getTableDetail(String databaseName, String tableName) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return tableGateway.getTableDetail(normalizedDb, tableName);
    }
}
