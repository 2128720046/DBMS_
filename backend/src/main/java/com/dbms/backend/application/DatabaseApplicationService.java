package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import com.dbms.backend.infrastructure.storage.RuankoBinaryCatalogService;
import com.dbms.backend.model.DatabaseInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseApplicationService {

    private final DatabaseDomainService domainService;
    private final DatabaseSchemaGateway schemaGateway;
    private final RuankoBinaryCatalogService binaryCatalogService;

    public DatabaseApplicationService(DatabaseDomainService domainService,
                                      DatabaseSchemaGateway schemaGateway,
                                      RuankoBinaryCatalogService binaryCatalogService) {
        this.domainService = domainService;
        this.schemaGateway = schemaGateway;
        this.binaryCatalogService = binaryCatalogService;
    }

    public void createDatabase(String dbName) {
        String normalizedDb = domainService.normalizeDatabaseName(dbName);
        schemaGateway.createSchema(normalizedDb);
        binaryCatalogService.createDatabaseArtifacts(normalizedDb);
    }

    public void dropDatabase(String dbName) {
        String normalizedDb = domainService.normalizeDatabaseName(dbName);
        schemaGateway.dropSchema(normalizedDb);
        binaryCatalogService.dropDatabaseArtifacts(normalizedDb);
    }

    public List<DatabaseInfo> listDatabases() {
        return schemaGateway.listSchemas().stream().map(DatabaseInfo::new).toList();
    }
}