package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.RecordGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RecordApplicationService {

    private final DatabaseDomainService domainService;
    private final RecordGateway recordGateway;

    public RecordApplicationService(DatabaseDomainService domainService, RecordGateway recordGateway) {
        this.domainService = domainService;
        this.recordGateway = recordGateway;
    }

    public int insert(String databaseName, String tableName, Map<String, Object> values) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return recordGateway.insert(databaseName, tableName, values);
    }

    public List<Map<String, Object>> query(String databaseName, String tableName,
                                           Map<String, Object> filters, Integer limit, Integer offset) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");

        int safeLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
        int safeOffset = offset == null ? 0 : Math.max(offset, 0);
        return recordGateway.query(databaseName, tableName, filters, safeLimit, safeOffset);
    }

    public int update(String databaseName, String tableName,
                      Map<String, Object> filters, Map<String, Object> values) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return recordGateway.update(databaseName, tableName, filters, values);
    }

    public int delete(String databaseName, String tableName, Map<String, Object> filters) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return recordGateway.delete(databaseName, tableName, filters);
    }
}
