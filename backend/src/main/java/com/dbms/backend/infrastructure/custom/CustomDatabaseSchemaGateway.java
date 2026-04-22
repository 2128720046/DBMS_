package com.dbms.backend.infrastructure.custom;

import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomDatabaseSchemaGateway implements DatabaseSchemaGateway {

    @Override
    public void createSchema(String schemaName) {
        throw new UnsupportedOperationException("CustomDatabaseSchemaGateway 尚未实现");
    }

    @Override
    public void dropSchema(String schemaName) {
        throw new UnsupportedOperationException("CustomDatabaseSchemaGateway 尚未实现");
    }

    @Override
    public List<String> listSchemas() {
        throw new UnsupportedOperationException("CustomDatabaseSchemaGateway 尚未实现");
    }
}