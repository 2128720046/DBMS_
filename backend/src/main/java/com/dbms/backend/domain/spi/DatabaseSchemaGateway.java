package com.dbms.backend.domain.spi;

import java.util.List;

public interface DatabaseSchemaGateway {

    void createSchema(String schemaName);

    void dropSchema(String schemaName);

    List<String> listSchemas();
}