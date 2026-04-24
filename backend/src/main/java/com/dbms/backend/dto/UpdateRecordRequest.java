package com.dbms.backend.dto;

import java.util.Map;

public class UpdateRecordRequest {

    private Map<String, Object> filters;
    private Map<String, Object> values;

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }
}
