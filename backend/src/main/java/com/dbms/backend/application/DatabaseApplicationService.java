package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import com.dbms.backend.model.DatabaseInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseApplicationService {

    private final DatabaseDomainService domainService;
    private final DatabaseSchemaGateway schemaGateway;

    public DatabaseApplicationService(DatabaseDomainService domainService, DatabaseSchemaGateway schemaGateway) {
        this.domainService = domainService;
        this.schemaGateway = schemaGateway;
    }

    public void createDatabase(String dbName) {
        String normalizedDbName = domainService.normalizeDatabaseName(dbName);
        schemaGateway.createSchema(normalizedDbName);
    }

    public void dropDatabase(String dbName) {
        String normalizedDbName = domainService.normalizeDatabaseName(dbName);
        schemaGateway.dropSchema(normalizedDbName);
    }

    public List<DatabaseInfo> listDatabases() {
        return schemaGateway.listSchemas().stream().map(DatabaseInfo::new).toList();
    }
    
}