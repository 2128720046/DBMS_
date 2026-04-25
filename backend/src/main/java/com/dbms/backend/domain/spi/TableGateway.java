package com.dbms.backend.domain.spi;

import com.dbms.backend.dto.ColumnDefinition;

import java.util.List;
import java.util.Map;

public interface TableGateway {

    void createTable(String schemaName, String tableName, List<ColumnDefinition> columns);

    void dropTable(String schemaName, String tableName);

    List<String> listTables(String schemaName);

    Map<String, Object> getTableDetail(String schemaName, String tableName);
}
