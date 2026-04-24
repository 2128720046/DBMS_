package com.dbms.backend.domain.spi;

import java.util.List;
import java.util.Map;

public interface RecordGateway {

    int insert(String schemaName, String tableName, Map<String, Object> values);

    List<Map<String, Object>> query(String schemaName, String tableName, Map<String, Object> filters, int limit, int offset);

    int update(String schemaName, String tableName, Map<String, Object> filters, Map<String, Object> values);

    int delete(String schemaName, String tableName, Map<String, Object> filters);
}
