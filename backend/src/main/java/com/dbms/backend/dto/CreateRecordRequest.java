package com.dbms.backend.dto;

import java.util.Map;

public class CreateRecordRequest {

    private Map<String, Object> values;

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }
}
