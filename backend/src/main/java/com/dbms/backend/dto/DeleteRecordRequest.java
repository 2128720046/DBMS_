package com.dbms.backend.dto;

import java.util.Map;

public class DeleteRecordRequest {

    private Map<String, Object> filters;

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }
}
